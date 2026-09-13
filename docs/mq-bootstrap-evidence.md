# MQ first-reference initialization, 2026-09-13

Evidence source: the locally extracted official Glutec APK at
`/Users/ctqwa/RE/29m-dump/mq_re/extracted/base.apk`, SHA-256
`de3c497d03d137fb42ac16460ad3a92b4cc96453c719aff48d4b7a2a3e8d5faf`.

`RuGlutec_Begin_Wearing07.intothedatabase2()` reads raw QR sensitivity
from `spare02` and the transmitter scale flag from `spare05`. When that flag
is `"1"`, it multiplies sensitivity by ten and truncates to one decimal place
(`BigDecimal.setScale(1, 1)`, where rounding mode 1 is DOWN). The BLE lookup
supplies `transmitter10`; the QR lookup alone cannot establish the scale.
Both QR bootstrap and continued-wear bootstrap must apply this conversion.

The first-reference call in
`RuGlutec_BloodGlucoseData_Service.onCharacteristicChanged()` uses these
arguments for `RuGlutec_GlucoseCalculationArrayNew.GlucoseCalculation` after
packet 18, when sensitivity is present and no reference has been established:

```
[1, packet, sampleCurrent, 0, previousReviseCurrent2, sensitivity, 0, 2, 0, packages, multiplier]
```

The first argument is the constant 1, not elapsed session minutes. The service
uses output index 7 as the initial reference. Passing elapsed time at startup
enables the calculator's smoothing against an empty previous sample. The
2026-09-13 trace reproduces that defect at packet 24: current 175 becomes
processed current 3, produces reference 17 (mmol/L times ten), then solves K
as 101.76. Subsequent samples remain near the calculator's lower bound.

`MQBootstrapSeedTests` checks the recovered initialization arguments and a
fixture with an explicitly selected 10x configuration: raw sensitivity 2.93
becomes 29.3; current 175 minus offset 2 gives processed current 173, reference
59, and solved K 29.32. This is source-derived calculation evidence, not a
paired official-app/device glucose measurement. The trace does not establish
the transmitter scale by itself, so production code requires BLE metadata.

The service method exceeds JADX's structured decompilation limits. To inspect
its instruction dump, use `jadx --single-class
com.ruapp.glutec.RuGlutec_BloodGlucoseData_Service --single-class-output
<output.java> --show-bad-code --comments-level debug <base.apk>`. Inspect the
initial-reference calls, not the separate later recalibration call.

Previously persisted K/B values and historical readings have no reliable
provenance identifying this startup bug. They are not rewritten by this fix.
A fresh local MQ registration and fresh QR/bootstrap lookup exercise the
corrected path; physical transmitter reset is not required for that test.

## Follow-up, 2026-09-14

The user reports the upper clamp (40 mmol/L) after QR bootstrap, followed by
usable readings after manual calibration. The supplied attachment is still
the earlier 1.7 mmol/L trace. Automatic calibration is therefore not considered
device-validated. A new trace must establish raw QR sensitivity, BLE
`transmitter10`, normalized sensitivity and the resulting K/B; the driver now
logs these without account credentials. Do not infer a scale from the displayed
glucose or overwrite the user's current calibration based on it.

Two additional defects were confirmed in source:

- `fetchBestEffortOnce` discarded `fetchContinueWearConfig`'s history by always
  returning an empty list. Valid continued-wear history now reaches its caller;
  reset suppression still prevents the session lookup.
- Both native mirror callers divided `mgdlTimes10` by ten, then the mirror
  divided by ten again. `g.cpp:storeGlucoseStreamSample` expects true mg/dL and
  performs its own internal times-ten conversion. `MQNativeGlucoseMirror`
  now passes `MQAlgorithm.Result.mgdl` directly and converts only milliseconds
  to seconds. Previously stored rows are not guessed or rewritten.

Short-gap BLE replay and cloud snapshot restore are separate mechanisms. No
full-history BLE request command has been verified. Disconnect diagnostics now
include phase, connection duration and protocol-frame age; the older trace's
status 147 alone does not establish the cause of link loss.
