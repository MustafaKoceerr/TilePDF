# Phase 14: Verifying Digital Signatures — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** A signed PDF opens with a "Signed" chip; a panel lists each signature with what a reader needs:
- who signed, when, and why;
- whether the signed bytes are intact;
- whether the signer's certificate chains to a trusted root;
- whether the document changed after signing.

**Architecture:**
- **Reading (`core:pdf`).** PDFium reads each signature: `FPDF_GetSignatureCount/Object` and `FPDFSignatureObj_GetContents/ByteRange/SubFilter/Reason/Time`. `PdfDocument.signatures()` returns them raw.
- **Checking (`core:signature`, new).** `SignatureVerifier` checks one signature.
  - It digests the byte ranges by streaming them from the file (`SignedBytes`), so large files aren't read into memory.
  - It parses the CMS with BouncyCastle and verifies it with the precomputed digest.
  - It checks the signing certificate's chain with PKIX against the trust anchors at the signing time. There's no revocation check, since TilePDF is offline.
  - Signatures and chains are checked with the platform's JCA. No BouncyCastle provider is registered, so R8 can't break algorithm lookup by reflection.
  - Android's anchors come from `AndroidCAStore`: the system CAs and the ones the user added.
- **Outcome.** `SignatureCheck(signer, issuer, signedAt, reason, integrity, trusted, changedAfterSigning)`.
  - `integrity` is `Intact`, `Broken` or `Unsupported`.
  - The signatures understood are `adbe.pkcs7.detached` and `ETSI.CAdES.detached`.
  - `changedAfterSigning`: the byte range doesn't reach the end of the file.
- **Saving keeps signatures intact.** Every save is already incremental (`FPDF_INCREMENTAL`), so a signed file's signed bytes stay as they are. A device test pins that down.

**Tech Stack:** BouncyCastle `bcpkix-jdk18on` / `bcprov-jdk18on` 1.86 (parsing only), JCA (Conscrypt), PDFium signature API, Compose.

**Spec:** the roadmap, phase 14. It is stacked on phase 13.

## Global Constraints
- **Offline.** No OCSP, CRL or AIA fetching. The panel says the revocation status wasn't checked.
- **A failure is a status, never a crash.** A signature that can't be parsed shows "can't be checked".
- **The open isn't slowed.** Checks run in the background after the document shows.

## Review Focus
- **A signature over the whole file** shows Intact, and changedAfterSigning is false.
- **The same file with an incremental update appended** shows Intact, and changedAfterSigning is true.
- **One flipped byte inside the signed range** shows Broken.
- **A self-signed certificate** shows Intact but untrusted.
- **A certificate that expired before signing** is untrusted.
- **The release build (R8)** verifies a signature on the emulator.

---

### Task 1: The verifier (JVM)
- `core/model`: `SignatureCheck` and `SignatureIntegrity`.
- `core/pdf`: `PdfSignature(contents: ByteArray, byteRange: List<Long>, subFilter: String, reason: String, time: String)` and `PdfDocument.signatures()`, which defaults to empty.
- `core/signature`:
  - `SignedBytes` (`fun read(offset, length, sink)`);
  - `SignatureVerifier(anchors: () -> Set<TrustAnchor>)` with `verify(signature, fileLength, bytes)`;
  - `AndroidTrustAnchors`.
- `SignatureVerifierTest`, using BouncyCastle's provider in tests only to make keys, certificates and CMS:
  - `wholeFileSignatureIsIntactAndTrusted`;
  - `appendedUpdateIsChangedAfterSigning`;
  - `flippedByteIsBroken`;
  - `selfSignedIsUntrusted`;
  - `expiredBeforeSigningIsUntrusted`;
  - `unknownSubFilterIsUnsupported`;
  - `garbageContentsIsBroken`;
  - `signerAndReasonAndTimeAreRead`.

### Task 2: Reading signatures (native)
- JNI `nativeSignatures(doc): Array<NativeSignature>`. `PdfiumDocument.signatures()` maps them and parses `D:` dates into `signedAt` (`pdfDate` helper, JVM-tested).
- `tools/generate_test_pdfs.py`: `signed.pdf`.
  - It is `sample.pdf` page 1 with an incremental update adding a signature field.
  - The `adbe.pkcs7.detached` CMS is made with Python `cryptography` and a throwaway key made at generation time. The key is never stored, so `signed.pdf` changes on every run.
- Device tests:
  - `PdfiumSignatureReadTest`: one signature with the byte range, subfilter and reason;
  - `SignedFileStaysIntactTest`: saving an annotation into `signed.pdf` keeps its signed bytes, and the verifier says Intact and changed after signing.

### Task 3: The reader
- `ReaderViewModel`, after open: `signatures()`, then the verifier on `PdfDispatcher` off the UI. It keeps `signatures: List<SignatureCheck>`.
- `ReaderScreen`:
  - a chip "Signed · Valid" or "Signed · Check" in the bottom area while the reader is at page 1;
  - the ⋮ "Signatures" item opens `SignaturesSheet`, with a row per signature: status icon, signer, time, reason and issuer. Below the rows, one line: "Revocation isn't checked: TilePDF works offline."
- Strings: EN and TR.
- JVM test: `signatureSummary(checks)`. It maps checks to the chip's state (all valid / some changed / some untrusted / some broken).

### Task 4: Docs and checks
- README.
- Store texts (one line).
- `testDebugUnitTest lintDebug assembleRelease checkReleasePermissions`, and the device tests.
- On the release build: open `signed.pdf`. The chip shows; the panel shows the signer, untrusted, "intact".
