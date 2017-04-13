package org.yamcs.xtce;

public class JavaFormulaCalibrator implements Calibrator {
    private static final long serialVersionUID = 1L;
    private final String javaFormula;
    public JavaFormulaCalibrator(String javaFormula) {
        this.javaFormula = javaFormula;
    }
}
