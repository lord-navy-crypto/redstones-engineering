#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors = []

def read(path):
    return (ROOT / path).read_text()

def need(body, token, label):
    if token not in body:
        errors.append(f"{label}: missing {token}")

def ban(body, token, label):
    if token in body:
        errors.append(f"{label}: unexpected {token}")

menu = read("src/main/java/dev/redstoneengineering/ui/menu/MediaConversionMenu.java")
screen = read("src/main/java/dev/redstoneengineering/client/ui/MediaConversionScreen.java")
reg = read("src/main/java/dev/redstoneengineering/ui/EngineeringUiRegistration.java")
client = read("src/main/java/dev/redstoneengineering/client/ui/EngineeringUiClientRegistration.java")
openers = read("src/main/java/dev/redstoneengineering/ui/FieldDeviceUi.java")
scaler = read("src/main/java/dev/redstoneengineering/block/RedstoneToLapisScalerBlock.java")
quantizer = read("src/main/java/dev/redstoneengineering/block/LapisToRedstoneQuantizerBlock.java")

for token in ["EngineeringPortProvider", "engineeringSnapshot", "CoreMediaDiagnostics.sourceCodeSpacing", "CoreMediaDiagnostics.lapisReconstructedFromRedstone", "CoreMediaDiagnostics.quantizationError", "commissioningStatus.set"]:
    need(menu, token, "menu")
for token in ["REDSTONE → LAPIS SCALER", "LAPIS → REDSTONE QUANTIZER", "UPSCALED REPRESENTATION — NO NEW SOURCE PRECISION", "Quantization loss", "SERVER-SYNCHRONIZED OBSERVER", "commissioningStatus()"]:
    need(screen, token, "screen")
for token in ["RedstoneObservationSupport", "PrecisionObservationSupport", "DomainNetwork.", "CoreMediaDiagnostics."]:
    ban(screen, token, "client isolation")
need(reg, "MEDIA_CONVERSION", "registration")
need(client, "MediaConversionScreen::new", "client registration")
need(openers, "new MediaConversionMenu", "opener")
need(scaler, "FieldDeviceUi.open(serverPlayer, pos)", "scaler entry")
need(quantizer, "FieldDeviceUi.open(serverPlayer, pos)", "quantizer entry")
need(scaler, "CoreMediaDiagnostics.lapisFromRedstone", "scaler backend")
need(quantizer, "CoreMediaDiagnostics.redstoneFromLapis", "quantizer backend")

if errors:
    print("RSE media conversion HMI verification: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("RSE media conversion HMI verification: PASS")
