package miniJava.CodeGeneration;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ContextualAnalyzer.IdChecker;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.mJAM.Machine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Encoder implements Visitor<Object,Object> {
    private ErrorReporter reporter;
    private int callMainAddress;
    private List<PatchLine> patchList;
    private boolean mainMethodFound;
    private int frameOffset;
    private MethodDecl currMethodDecl;

    public Encoder(ErrorReporter reporter) {
        this.reporter = reporter;
        this.patchList = new ArrayList<>();
        this.mainMethodFound = false;
    }

    public void encode(AST ast) {
        Machine.initCodeGen();
        Machine.emit(Machine.Op.LOADL,0);            // array length 0
        Machine.emit(Machine.Prim.newarr);           // empty String array argument
        callMainAddress = Machine.nextInstrAddr();  // record instr addr where main is called                                                // "main" is called
        Machine.emit(Machine.Op.CALL, Machine.Reg.CB,-1);     // static call main (address to be patched)
        Machine.emit(Machine.Op.HALT,0,0,0);         // end execution
        ast.visit(this, null);
        for(PatchLine pl : patchList) pl.patch();
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        // Allocate memory to static fields of each class
        // Define runtime entities for all FieldDecl of each class
        int staticOffset = 0;
        for (ClassDecl cd : prog.classDeclList) {
            int nonstaticOffset = 0;
            for (FieldDecl fd : cd.fieldDeclList) {
                if (fd.isStatic) {
                    fd.runtimeEntity = new RuntimeEntity(staticOffset++, 1);
                    Machine.emit(Machine.Op.PUSH, 1);
                }
                else {
                    fd.runtimeEntity = new RuntimeEntity(nonstaticOffset++, Machine.integerSize);
                }
            }
            cd.nonStaticFieldCount = nonstaticOffset;
        }
        for (ClassDecl cd : prog.classDeclList) cd.visit(this, null);
        if(!mainMethodFound) {
            reporter.reportError("*** Runtime Error at line " + prog.posn.start + ": Main method not found");
            System.exit(4);
        }
        return null;
    }

    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        for (MethodDecl md : cd.methodDeclList) md.visit(this, null);
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {return null; }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.runtimeEntity = new RuntimeEntity(Machine.nextInstrAddr(), Machine.addressSize);
        // Check if it is main method
        if (md.isStatic &&
                !md.isPrivate &&
                md.type.typeKind==TypeKind.VOID &&
                md.parameterDeclList.size()==1 &&
                md.parameterDeclList.get(0).type.typeKind == TypeKind.ARRAY &&
                ((ArrayType)md.parameterDeclList.get(0).type).eltType.typeKind == TypeKind.CLASS &&
                ((ClassType)((ArrayType)md.parameterDeclList.get(0).type).eltType).className.spelling.equals("String")) {
            if (mainMethodFound) {
                reporter.reportError("*** Runtime Error at line " + md.posn.start + ": Duplicate main method");
                System.exit(4);
            }
            else {
                mainMethodFound = true;
                patchList.add(new PatchLine(callMainAddress, md));
            }
        }
        // Define runtime entities for each parameter of the method
        int paramOffset = -1*md.parameterDeclList.size();
        for(ParameterDecl pd : md.parameterDeclList) pd.runtimeEntity = new RuntimeEntity(paramOffset++, 1);
        // Body
        frameOffset = 3;
        currMethodDecl = md;
        for (Statement stmt : md.statementList) stmt.visit(this, null);
        // Return
        if (md.type.typeKind == TypeKind.VOID) Machine.emit(Machine.Op.RETURN, 0,0, md.parameterDeclList.size());
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {return null; }
    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {return null; }
    @Override
    public Object visitBaseType(BaseType type, Object arg) {return null; }
    @Override
    public Object visitClassType(ClassType type, Object arg) {return null; }
    @Override
    public Object visitArrayType(ArrayType type, Object arg) {return null; }

    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        int varDeclCount = 0;
        for (Statement s : stmt.sl) {
            if (s instanceof VarDeclStmt) varDeclCount++;
            s.visit(this,null);
        }
        Machine.emit(Machine.Op.POP, varDeclCount);
        frameOffset -= varDeclCount;
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.initExp.visit(this, null);
        VarDecl vd = stmt.varDecl;
        if (vd.type.typeKind == TypeKind.INT) vd.runtimeEntity = new RuntimeEntity(frameOffset++, Machine.integerSize);
        else if (vd.type.typeKind == TypeKind.BOOLEAN) vd.runtimeEntity = new RuntimeEntity(frameOffset++, Machine.booleanSize);
        else vd.runtimeEntity = new RuntimeEntity(frameOffset++, Machine.addressSize);
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        Declaration decl = stmt.ref.decl;
        if (decl instanceof FieldDecl) {
            // a.b = c
            if (stmt.ref instanceof QualRef) {
                ((QualRef)stmt.ref).ref.visit(this,null);
                Machine.emit(Machine.Op.LOADL, decl.runtimeEntity.offset);
                stmt.val.visit(this,null);
                Machine.emit(Machine.Prim.fieldupd);
            }
            // a = b; where a is a field
            else {
                stmt.val.visit(this,null);
                Machine.emit(Machine.Op.STORE, decl.runtimeEntity.size, Machine.Reg.OB, decl.runtimeEntity.offset);
            }
        }
        // a = b; where a is a parameter or a variable
        else {
            stmt.val.visit(this,null);
            Machine.emit(Machine.Op.STORE, decl.runtimeEntity.size, Machine.Reg.LB, decl.runtimeEntity.offset);
        }
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.ref.visit(this,null);
        stmt.ix.visit(this,null);
        stmt.exp.visit(this,null);
        Machine.emit(Machine.Prim.arrayupd);
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        for (Expression expr : stmt.argList) expr.visit(this, null);
        if (stmt.methodRef.decl == IdChecker.PRINTLN) {
            Machine.emit(Machine.Prim.putintnl);
        }
        else if (((MethodDecl)stmt.methodRef.decl).isStatic) {
            patchList.add(new PatchLine(Machine.nextInstrAddr(), stmt.methodRef.decl));
            Machine.emit(Machine.Op.CALL, Machine.Reg.CB, -1);
        }
        else {
            if (stmt.methodRef instanceof QualRef) {
                ((QualRef)stmt.methodRef).ref.visit(this,null);
            }
            else {
                Machine.emit(Machine.Op.LOADA, Machine.Reg.OB, 0);
            }
            patchList.add(new PatchLine(Machine.nextInstrAddr(), stmt.methodRef.decl));
            Machine.emit(Machine.Op.CALLI, Machine.Reg.CB, -1);
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        if(stmt.returnExpr != null) {
            stmt.returnExpr.visit(this,null);
            Machine.emit(Machine.Op.RETURN, 1,0, currMethodDecl.parameterDeclList.size());
        }
        Machine.emit(Machine.Op.RETURN, 0,0, currMethodDecl.parameterDeclList.size());
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.cond.visit(this,null);
        int j = Machine.nextInstrAddr();
        Machine.emit(Machine.Op.JUMPIF,0,Machine.Reg.CB,-1);
        stmt.thenStmt.visit(this,null);
        if (stmt.elseStmt != null) {
            int k = Machine.nextInstrAddr();
            Machine.emit(Machine.Op.JUMP,Machine.Reg.CB,-1);
            Machine.patch(j,Machine.nextInstrAddr());
            stmt.elseStmt.visit(this,null);
            Machine.patch(k,Machine.nextInstrAddr());
        }
        else {
            Machine.patch(j,Machine.nextInstrAddr());
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        int j = Machine.nextInstrAddr();
        Machine.emit(Machine.Op.JUMP, Machine.Reg.CB, -1);
        int g = Machine.nextInstrAddr();
        stmt.body.visit(this, null);
        Machine.patch(j,Machine.nextInstrAddr());
        stmt.cond.visit(this,null);
        Machine.emit(Machine.Op.JUMPIF, 1, Machine.Reg.CB, g);
        return null;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.expr.visit(this,null);
        if (expr.operator.kind == TokenKind.MI) Machine.emit(Machine.Prim.neg);
        else Machine.emit(Machine.Prim.neg);
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.left.visit(this,null);
        expr.right.visit(this,null);
        switch(expr.operator.kind) {
            case OR:
                Machine.emit(Machine.Prim.or);
                break;
            case AND:
                Machine.emit(Machine.Prim.and);
                break;
            case EQ:
                Machine.emit(Machine.Prim.eq);
                break;
            case NE:
                Machine.emit(Machine.Prim.ne);
                break;
            case LT:
                Machine.emit(Machine.Prim.lt);
                break;
            case LE:
                Machine.emit(Machine.Prim.le);
                break;
            case GT:
                Machine.emit(Machine.Prim.gt);
                break;
            case GE:
                Machine.emit(Machine.Prim.ge);
                break;
            case ADD:
                Machine.emit(Machine.Prim.add);
                break;
            case MI:
                Machine.emit(Machine.Prim.sub);
                break;
            case MUL:
                Machine.emit(Machine.Prim.mult);
                break;
            default: //DIV
                Machine.emit(Machine.Prim.div);
                break;
        }
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.ref.visit(this,null);
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.ref.visit(this,null);
        expr.ixExpr.visit(this,null);
        Machine.emit(Machine.Prim.arrayref);
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        for (Expression e : expr.argList) e.visit(this, null);
        if (expr.functionRef.decl == IdChecker.PRINTLN) {
            Machine.emit(Machine.Prim.putintnl);
        }
        else if (((MethodDecl)expr.functionRef.decl).isStatic) {
            patchList.add(new PatchLine(Machine.nextInstrAddr(), expr.functionRef.decl));
            Machine.emit(Machine.Op.CALL, Machine.Reg.CB, -1);
        }
        else {
            if (expr.functionRef instanceof QualRef) {
                ((QualRef)expr.functionRef).ref.visit(this,null);
            }
            else {
                Machine.emit(Machine.Op.LOADA, Machine.Reg.OB, 0);
            }
            patchList.add(new PatchLine(Machine.nextInstrAddr(), expr.functionRef.decl));
            Machine.emit(Machine.Op.CALLI, Machine.Reg.CB, -1);
        }
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        int value;
        switch(expr.lit.kind) {
            case NUM:
                value = Integer.parseInt(expr.lit.spelling);
                break;
            case TRUE:
                value = 1;
                break;
            default: // FALSE/NULL
                value = 0;
                break;
        }
        Machine.emit(Machine.Op.LOADL, value);
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        Machine.emit(Machine.Op.LOADL, -1);
        Machine.emit(Machine.Op.LOADL, ((ClassDecl)expr.classtype.className.decl).nonStaticFieldCount);
        Machine.emit(Machine.Prim.newobj);
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.sizeExpr.visit(this, null);
        Machine.emit(Machine.Prim.newarr);
        return null;
    }

    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        Machine.emit(Machine.Op.LOADA, Machine.Reg.OB, 0);
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        if (ref.decl instanceof FieldDecl) {
            if (!((FieldDecl) ref.decl).isStatic) {
                Machine.emit(Machine.Op.LOAD, Machine.Reg.OB, ref.decl.runtimeEntity.offset);
            }
            else {
                Machine.emit(Machine.Op.LOAD, Machine.Reg.SB, ref.decl.runtimeEntity.offset);
            }
        }
        else if (ref.decl instanceof ParameterDecl || ref.decl instanceof VarDecl){
            Machine.emit(Machine.Op.LOAD, Machine.Reg.LB, ref.decl.runtimeEntity.offset);
        }
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        if (ref.decl instanceof FieldDecl) {
            if (((FieldDecl) ref.decl).isStatic) {
                Machine.emit(Machine.Op.LOAD, Machine.Reg.OB, ref.decl.runtimeEntity.offset);
            }
            else {
                ref.ref.visit(this,null); // Address of baseRef on stack top
                if(ref.decl == IdChecker.ARRAY_LENGTH) {
                    Machine.emit(Machine.Prim.arraylen);
                }
                else {
                    //if(ref.id.decl == null) System.out.println(ref.id.spelling);
                    Machine.emit(Machine.Op.LOADL, ref.decl.runtimeEntity.offset);
                    Machine.emit(Machine.Prim.fieldref);
                }
            }
        }
        return null;
    }

    @Override
    public Object visitIdentifier(Identifier id, Object arg) { return null; }
    @Override
    public Object visitOperator(Operator op, Object arg) { return null; }
    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) { return null; }
    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) { return null; }
    @Override
    public Object visitNullLiteral(NullLiteral literal, Object arg) { return null; }

    public static void main(String[] args) {
        System.out.println("hi");
    }
}
