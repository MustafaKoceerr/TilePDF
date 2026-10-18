# Phase 15: Signing with a Certificate (PAdES) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** "Sign with certificate" puts a visible, cryptographic PAdES B-B signature into the document. The certificate comes from Android's own certificate store; a `.p12`/`.pfx` file can be installed there from the app. TilePDF's verifier (phase 14) and other readers then show the signature.

**Architecture:**
- **Appearance (PDFium).** The editable document gets a Stamp annotation in the chosen box. Its appearance is drawn with PDFium's page objects:
  - the signer's name and the date, in the Noto Sans subset;
  - the drawn signature from phase 9 as paths, if the user has one.
  - It carries a unique `/NM`. The document is then saved incrementally to a temp file.
- **Signing (`core:pdfwriter`, new, pure Kotlin, JVM-tested).**
  - **`PdfFile`** reads the temp file:
    - the last trailer, then each `/Prev`;
    - classic xref tables and xref streams (`/W`, `/Index`, Flate with the PNG Up predictor);
    - objects inside object streams;
    - a lexer for every object type.
  - **`IncrementalUpdate`** appends new and replaced objects and one xref section of the same kind as the last (table or stream) with `/Prev`.
  - **`PdfSigner`** does the rest:
    - it finds the Stamp by `/NM` and rewrites it as a `/FT /Sig` widget, keeping `/Rect`, `/AP` and `/P`;
    - it adds the signature dictionary (`/Filter /Adobe.PPKLite /SubFilter /ETSI.CAdES.detached /M`, `/Reason`, and a 16 KB `/Contents` hole with a fixed-width `/ByteRange`);
    - it adds the field to the AcroForm `/Fields` with `/SigFlags 3`, creating the AcroForm and replacing the catalog if needed;
    - it writes the update, fills `/ByteRange`, has the bytes signed, and writes the CMS hex into the hole.
- **CMS (`core:signature`).** `CmsSigner` builds a detached CMS with BouncyCastle: signed attributes content-type, message-digest and signing-certificate-v2, and no signing-time, as PAdES B-B wants. It signs with the `PrivateKey` through the platform JCA, so a KeyChain hardware key works.
- **Flow (`core:data` `CertificateSigning`).**
  1. Save the draft first.
  2. Open for editing and add the Stamp; save to temp.
  3. `PdfSigner` writes the signed temp.
  4. Phase 14's verifier checks it is Intact.
  5. Replace the file with the safe save's `files.replace`, and reopen.
  - A file TilePDF can't write to fails with NoWritePermission; the reader offers Save as copy first.
- **Reader.**
  - The ⋮ menu gets "Sign with certificate". It opens `KeyChain.choosePrivateKeyAlias` (the system dialog, which can also install). A secondary button, "Install certificate file…", opens `KeyChain.createInstallIntent()` with the picked file.
  - With an alias, the hint "Tap where the signature goes" shows. A tap places a 180 × 60 pt box centred there, clamped to the page, and the signing runs with progress; a toast tells the outcome.

**Tech Stack:** Kotlin, BouncyCastle 1.86 (CMS building), Android KeyChain, PDFium edit API.

**Spec:** the roadmap, phase 15. It is stacked on phase 14.

## Global Constraints
- **No key material is stored.** The alias is not kept either; each signing asks again.
- **No network.** No timestamp authority, so the profile is PAdES B-B.
- **The file is replaced only after verification.** The signed temp must verify Intact first.
- **Signing a signed document keeps the earlier signatures intact,** since every save is incremental.

## Review Focus
- **A file with an xref stream and object streams,** as PDFium and Acrobat often write: it is signed and still opens, and the earlier objects resolve.
- **Signing twice:** both signatures are Intact. The first shows "changed after signing", the second doesn't.
- **A document without an AcroForm** gets one, and the catalog is replaced.
- **The `/Contents` hole is too small for the CMS:** a clear failure, never a corrupt file.
- **A key the system won't release** (the user denied access): nothing is written.

---

### Task 1: Reading a PDF (`core:pdfwriter`, JVM)
- `PdfObject` (Null, Bool, Number, Str, Name, Array, Dict, Ref, Stream), `PdfLexer`, and `PdfFile(bytes)` with `trailer`, `resolve(ref)`, `objectNumbers` and `startxref`.
- `PdfFileTest`, on files built in the test:
  - classic xref, with the objects read;
  - an incremental update with `/Prev`, where the newest wins;
  - an xref stream with predictor 12;
  - an object inside an object stream;
  - literal string escapes and hex strings;
  - nested dicts and arrays.
- It is also run on the repo's `signed.pdf` and `forms.pdf` assets: every object resolves.

### Task 2: Writing an incremental update (JVM)
- `PdfWriter.serialize(PdfObject)`.
- `IncrementalUpdate(file).apply { set(num, obj); add(obj) }.write(): ByteArray`.
  - It writes an xref table, or a stream if the last section was one.
  - `/Size`, `/Root`, `/Info` and `/ID` are carried over.
- `IncrementalUpdateTest`:
  - the original bytes come first unchanged;
  - reparsing gives the new objects and the old ones;
  - after an xref stream, the update is an xref stream.

### Task 3: Signing (JVM)
- `PdfSigner.sign(file, stampName, signedAt, reason, signature: (ByteArray) -> ByteArray): ByteArray`.
- `CmsSigner(privateKey, chain).sign(content): ByteArray`, in `core:signature`.
- Tests:
  - `PdfSignerTest`:
    - the widget replaces the stamp;
    - the AcroForm is made when missing;
    - the ByteRange covers everything but the hole;
    - a too-small hole fails;
    - signing twice keeps the first intact;
  - `CmsSignerTest`: the signed attributes are there, without signing-time;
  - end to end: sign with `CmsSigner` and check with phase 14's `SignatureVerifier`, which says Intact, trusted with the test root, and not changed after signing.

### Task 4: The appearance (native) and the flow
- JNI `nativeAddSignatureStamp(doc, page, rect, name, lines: Array<String>, strokes, strokeSizes): Boolean`. It adds a Stamp with `/NM`, text objects in the fallback font, and path objects for the strokes.
- `core:data` `CertificateSigning.sign(uri, alias, pageIndex, rect): SignResult`, using `KeyChainKeys` (getPrivateKey / getCertificateChain, off the main thread).
- Device test `PdfiumSignatureStampTest`: the stamp is added and saved, and PDFium reads the `/NM` back. Also `PdfSignerDeviceTest`: sign `sample.pdf` with a generated key in the test; PDFium then reads one signature, and the verifier says Intact.

### Task 5: The reader
- VM: `onSignWithCertificate` → screen effect `ChooseCertificate`; `onCertificateChosen(alias)` → `SigningStep.Place`; a tap → signing job (progress); the result goes to a toast and a reopen.
- Screen:
  - the menu item;
  - `KeyChain.choosePrivateKeyAlias` from the Activity;
  - the "Install certificate file…" action: SAF pick, then `createInstallIntent` with the bytes;
  - the hint.
- Strings: EN and TR.

### Task 6: Docs and checks
- README, store texts, privacy (no keys stored; the certificate stays in Android's store).
- The full suite, lint, release and permissions; the device tests.
- By hand on the emulator: install a test `.p12`, sign `sample.pdf`, reopen. The chip shows "Signed · signer not verified", or valid if the test root was installed as a CA.
