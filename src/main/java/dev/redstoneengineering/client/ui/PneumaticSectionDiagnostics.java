package dev.redstoneengineering.client.ui;

import dev.redstoneengineering.core.port.PortQuality;

/** Pure HMI interpretation of already-synchronized pneumatic witness pressures. */
final class PneumaticSectionDiagnostics {
    private PneumaticSectionDiagnostics() {}

    record Result(int upstreamDrop, int meterDrop, int downstreamDrop, String localization, String nextAction) {}

    static Result analyze(
            int upstreamPressure, PortQuality upstreamQuality,
            int inletPressure, PortQuality inletQuality,
            int outletPressure, PortQuality outletQuality,
            int downstreamPressure, PortQuality downstreamQuality,
            int meterDrop
    ) {
        if (inletQuality != PortQuality.VALID || outletQuality != PortQuality.VALID) {
            return new Result(-1, Math.max(0, meterDrop), -1,
                    "LOCALIZATION • METER EVIDENCE INCOMPLETE",
                    "NEXT • restore valid meter-port evidence before localizing pressure loss.");
        }

        int up = upstreamQuality == PortQuality.VALID ? Math.max(0, upstreamPressure - inletPressure) : -1;
        int middle = Math.max(0, meterDrop);
        int down = downstreamQuality == PortQuality.VALID ? Math.max(0, outletPressure - downstreamPressure) : -1;

        int max = middle;
        String where = "METER SECTION";
        if (up >= 0 && up > max) { max = up; where = "UPSTREAM SECTION"; }
        if (down >= 0 && down > max) { max = down; where = "DOWNSTREAM SECTION"; }

        if (max <= 2) {
            if (up >= 0 && down >= 0) {
                return new Result(up, middle, down,
                        "LOCALIZATION • NO DOMINANT LOCAL DROP",
                        "NEXT • local pressure drop is coherent; compare another section or operating condition.");
            }
            return new Result(up, middle, down,
                    "LOCALIZATION • LOCAL DROP LOW • AXIAL WITNESSES PARTIAL",
                    "NEXT • extend axial evidence with another aligned flow meter before diagnosing beyond this section.");
        }

        String next = switch (where) {
            case "UPSTREAM SECTION" -> "NEXT • inspect the section before the meter for restriction, valve state or supply starvation.";
            case "DOWNSTREAM SECTION" -> "NEXT • inspect the section after the meter for restriction or downstream demand.";
            default -> "NEXT • inspect the immediate Pin→Pout section before changing supply pressure.";
        };
        return new Result(up, middle, down, "LOCALIZATION • DOMINANT DROP AT " + where, next);
    }
}
