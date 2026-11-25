# Backend API: face template storage

The app matches faces **on the device**. The backend only stores the enrolled templates and hands them
back on re-login or a new device. It never reads the embeddings and never decides whether a face matched.

## Conventions

- HTTPS only. Every call carries `Authorization: Bearer <token>`; the user id comes from the token, never the body.
- Embeddings are sent as **base64 of a float32 array, little-endian**. This is the network format; the
  local store (`FaceRecordCodec`) is big-endian and unrelated.
- The app uses **MobileFaceNet only** (`modelId: "mobilefacenet"`, 192-dim float32, L2-normalised; input
  `[2, 112, 112, 3]`, output `[2, 192]`). `modelId` and `dimension` are still sent so a future model change
  does not silently break stored templates.
- One enrollment produces 5 to 8 templates (`enrollMinKept = 5`, `enrollFrontFrames = 8`). The app's codec
  allows up to 16 per record.

## PUT /v1/face/template

Enroll upload. Replaces any existing template for the user, so it is idempotent and also serves re-enroll.

Request:

```json
{
  "recordId": "uuid",
  "name": "Mehedi",
  "enrolledAtMillis": 1764000000000,
  "modelId": "mobilefacenet",
  "pipelineVersion": 4,
  "dimension": 192,
  "templates": ["<base64 float32 LE>", "<base64 float32 LE>"]
}
```

Response `200`:

```json
{ "recordId": "uuid", "templateCount": 5 }
```

Server validation:

- `modelId` is `mobilefacenet` and `dimension` is `192`. Reject anything else with `INVALID_TEMPLATE`.
- `templates` has 1 to 16 entries.
- Each template decodes to exactly `dimension * 4` bytes.

## GET /v1/face/template

Returns the stored template for the authenticated user, for re-login or a new device.

Response `200`:

```json
{
  "recordId": "uuid",
  "name": "Mehedi",
  "enrolledAtMillis": 1764000000000,
  "modelId": "mobilefacenet",
  "pipelineVersion": 4,
  "dimension": 192,
  "templates": ["<base64 float32 LE>", "<base64 float32 LE>"]
}
```

Response `404`:

```json
{ "error": "NOT_ENROLLED", "message": "No face template stored for this user" }
```

The client checks `modelId` and `pipelineVersion` with `FaceRecord.isUsableWith`. Embeddings from a
different pipeline (detector mode, alignment, crop) are close but not equal and compare lower, which looks
like false rejects. If `pipelineVersion` (or `modelId`) differs from the running app, the template cannot
be used and the user must enroll again. The backend only stores and returns these two fields.

## Example

A demo body for `PUT /v1/face/template`; `GET` returns the same shape. The data is synthetic (two 192-dim
unit vectors with a little noise around a shared base), not taken from a real face. Each template is
192 × 4 = 768 bytes, which is 1024 base64 characters. A real enrollment has 5 to 8 templates; two are shown to keep this short.

```json
{
  "recordId": "2b7604fe-03e5-f684-81e6-d6c8e14aa460",
  "name": "Demo User",
  "enrolledAtMillis": 1764000000000,
  "modelId": "mobilefacenet",
  "pipelineVersion": 4,
  "dimension": 192,
  "templates": [
    "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"
  ]
}
```

## Errors

Body: `{ "error": "CODE", "message": "..." }`

| HTTP | error | When |
|---|---|---|
| 400 | `INVALID_TEMPLATE` | `modelId`/`dimension` is not MobileFaceNet's, or size or count does not match |
| 401 | `UNAUTHENTICATED` | token missing or expired |
| 404 | `NOT_ENROLLED` | no template stored for the user |

## Storage notes

- Keep `templates` as an opaque encrypted blob (AES-GCM, key in a KMS). It is never queried.
- Never log request or response bodies.
- Suggested table: `face_templates(user_id, record_id, name, model_id, pipeline_version, dimension, blob, enrolled_at, updated_at)`.
