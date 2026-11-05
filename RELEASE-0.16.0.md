# StreamNest 0.16.0

Pair devices with an eight-character code that expires after five minutes and works once. In Resume & sync devices, create a group if needed and choose Show pairing code. Enter that code on the second device and confirm the connection.

Scan the QR with the phone camera. The HTTPS page offers Open StreamNest, which fills the code for confirmation. There is no built-in camera scanner. The original recovery key remains available through Show recovery key, and existing connected devices keep their group.

The backend stores encrypted temporary pairing tickets. Tests cover expiry, one-time use, concurrent redemption, tampering and desktop-to-Android encryption compatibility. Physical camera scanning still needs device verification.
