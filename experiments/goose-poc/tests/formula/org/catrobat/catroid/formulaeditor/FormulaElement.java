package org.catrobat.catroid.formulaeditor;
import java.util.ArrayList;
import java.util.List;
/** Minimal mutable model fixture for testing the real GooseNumericProgram. */
final class FormulaElement {
    enum ElementType { NUMBER, BRACKET, OPERATOR, USER_VARIABLE }
    ElementType type;
    String value;
    Double cached;
    FormulaElement leftChild, rightChild;
    List<FormulaElement> additionalChildren = new ArrayList<>();
    FormulaElement(ElementType type, String value, FormulaElement left, FormulaElement right) {
        this.type = type; this.value = value; leftChild = left; rightChild = right;
    }
    String gooseRawValue() { return value; }
    Double gooseNumericLiteral() {
        if (cached != null) return cached;
        if (value == null) return null;
        try { cached = Double.valueOf(value); } catch (NumberFormatException e) { return null; }
        return cached;
    }
}
