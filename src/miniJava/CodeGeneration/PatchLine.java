package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.Declaration;
import miniJava.mJAM.Machine;

public class PatchLine {
    private int codeAddr;
    private Declaration decl;
    public PatchLine(int codeAddr, Declaration decl) {
        this.codeAddr = codeAddr;
        this.decl = decl;
    }

    public void patch() {
        Machine.patch(codeAddr, decl.runtimeEntity.offset);
    }
}
