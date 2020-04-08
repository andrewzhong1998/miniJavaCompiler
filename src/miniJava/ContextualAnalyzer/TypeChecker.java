package miniJava.ContextualAnalyzer;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.TokenKind;

public class TypeChecker implements Visitor<Object, Object> {
    private ErrorReporter reporter;
    public static final BaseType INT = new BaseType(TypeKind.INT, null);
    public static final BaseType BOOLEAN = new BaseType(TypeKind.BOOLEAN, null);
    public static final BaseType NULL = new BaseType(TypeKind.ANY, null);
    public static final BaseType ERROR = new BaseType(TypeKind.ERROR, null);

    public TypeChecker(ErrorReporter reporter){
        this.reporter = reporter;
    }

    public void check(AST ast){
        ast.visit(this, null);
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        for (ClassDecl classDecl : prog.classDeclList) classDecl.visit(this, arg);
        return null;
    }

    /*
     *
     * Declaration
     *
     */
    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        for (FieldDecl fieldDecl : cd.fieldDeclList) fieldDecl.visit(this, arg);
        for (MethodDecl methodDecl : cd.methodDeclList) methodDecl.visit(this, arg);
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        TypeDenoter returnType = (TypeDenoter) md.type.visit(this,null);
        boolean returned = false;

        for (ParameterDecl parameterDecl : md.parameterDeclList) parameterDecl.visit(this, arg);

        for (Statement stmt : md.statementList) {
            // Pass in the return type of the method
            // statement should return false if it does not return anything and return true otherwise
            if((boolean)stmt.visit(this, returnType)) returned = true;
        }

        if((returnType.typeKind != TypeKind.VOID) && !returned) reporter.reportError("*** Type Error at line " + md.posn.start +": Missing return statement");
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        return null;
    }


    /*
     *
     * Type
     *
     */
    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        return type;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        if (type.className.spelling.equals("String")) return new BaseType(TypeKind.UNSUPPORTED, type.posn);
        return type;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        TypeDenoter typeDenoter = (TypeDenoter)type.eltType.visit(this, null);
        return new ArrayType(typeDenoter, type.posn);
    }

    /*
     *
     * Statement
     *
     */
    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        boolean returned = false;
        for(Statement s : stmt.sl) {
            if ((boolean)s.visit(this, arg)) returned = true;
        }
        return returned;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.initExp.visit(this, null);
        if(!stmt.initExp.type.equals(stmt.varDecl.type.visit(this,null))) reportTypeIncompatibleError(stmt.posn);
        return false;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.val.visit(this, null);
        if(!stmt.val.type.equals(stmt.ref.decl.type.visit(this,null))) reportTypeIncompatibleError(stmt.posn);
        return false;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        stmt.exp.visit(this,null);
        stmt.ix.visit(this,null);
        if(stmt.ref.decl.type instanceof ArrayType){
            if(!stmt.ix.type.equals(INT)) reporter.reportError("*** Type Error at line " + stmt.ix.type + ": INT type expected");
            if(!stmt.exp.type.equals(((ArrayType) stmt.ref.decl.type).eltType.visit(this,null))) reportTypeIncompatibleError(stmt.exp.posn); // Check incompatible types
        }
        else reporter.reportError("*** Type Error at line " + stmt.ref.posn.start + ": BaseType/ClassType is not addressable");
        return false;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        ParameterDeclList pdl = ((MethodDecl) stmt.methodRef.decl).parameterDeclList;
        for (int i=0; i<pdl.size(); i++) {
            Expression e = stmt.argList.get(i);
            e.visit(this, null);
            if(!e.type.equals(pdl.get(i).type)) reportTypeIncompatibleError(e.posn);
        }
        return false;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        TypeDenoter type = (TypeDenoter) arg;
        if (stmt.returnExpr != null) {
            stmt.returnExpr.visit(this, null);
            if (!stmt.returnExpr.type.equals(type)) {
                reportTypeIncompatibleError(stmt.posn);
            }
        }
        else if (type.typeKind != TypeKind.VOID) reporter.reportError("*** Type Error at line " + stmt.posn.start + ": Missing return value");
        return true;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.cond.visit(this, null);
        if(!stmt.cond.type.equals(BOOLEAN)) reporter.reportError("*** Type Error at line " + stmt.cond.posn.start + ": BOOLEAN type expected");
        boolean thenReturned = (boolean) stmt.thenStmt.visit(this, arg);
        if(stmt.elseStmt != null) {
            boolean elseReturned = (boolean) stmt.elseStmt.visit(this, arg);
            return thenReturned && elseReturned;
        }
        else{
            return thenReturned;
        }
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.cond.visit(this, null);
        if(!stmt.cond.type.equals(BOOLEAN)) reporter.reportError("*** Type Error at line " + stmt.cond.posn.start + ": BOOLEAN type expected");
        return stmt.body.visit(this, arg);
    }


    /*
     *
     * Expression
     *
     */
    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.expr.visit(this, null);
        expr.type = expr.expr.type;
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.left.visit(this, null);
        expr.right.visit(this, null);
        String op = expr.operator.spelling;
        if(op.equals("||") || op.equals("&&")) {
            if(!expr.left.type.equals(BOOLEAN)) reporter.reportError("*** Type Error at line " + expr.left.posn.start + ": BOOLEAN type expected");
            if(!expr.right.type.equals(BOOLEAN)) reporter.reportError("*** Type Error at line " + expr.right.posn.start + ": BOOLEAN type expected");
            expr.type = BOOLEAN;
        }
        if(op.equals("==") || op.equals("!=")) {
            if(!expr.left.type.equals(expr.right.type)) reporter.reportError("*** Type Error at line " + expr.posn.start + ": LHS should be of the same type as RHS");
            expr.type = BOOLEAN;
        }
        if(op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            if(!expr.left.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.left.posn.start + ": INT type expected");
            if(!expr.right.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.right.posn.start + ": INT type expected");
            expr.type = BOOLEAN;
        }
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            if(!expr.left.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.left.posn.start + ": INT type expected");
            if(!expr.right.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.right.posn.start + ": INT type expected");
            expr.type = INT;
        }
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.type = (TypeDenoter) expr.ref.decl.type.visit(this,null); // Check unsupported
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.ixExpr.visit(this, null);
        if(!expr.ixExpr.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.ixExpr.posn.start + ": INT type expected");
        if(expr.ref.decl.type instanceof ArrayType) expr.type = ((ArrayType) expr.ref.decl.type.visit(this,null)).eltType;
        else {
            reporter.reportError("*** Type Error at line " + expr.ref.posn.start + ": BaseType/ClassType is not addressable");
            expr.type = ERROR; // Error type
        }
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        ParameterDeclList pdl = ((MethodDecl) expr.functionRef.decl).parameterDeclList;
        for (int i=0; i<pdl.size(); i++) {
            Expression e = expr.argList.get(i);
            e.visit(this, null);
            TypeDenoter paramDeclType = (TypeDenoter) pdl.get(i).type.visit(this,null); // Check unsupported
            if(!e.type.equals(paramDeclType)) reportTypeIncompatibleError(e.posn);
        }
        expr.type = (TypeDenoter) expr.functionRef.decl.type.visit(this,null); // Check unsupported
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        // LiteralExpr ::= num | true | false | null
        if(expr.lit.kind == TokenKind.FALSE || expr.lit.kind == TokenKind.TRUE) expr.type = BOOLEAN;
        else if(expr.lit.kind == TokenKind.NUM) expr.type = INT;
        else expr.type = NULL;
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        expr.type = (TypeDenoter) expr.classtype.visit(this,null); // Check unsupported
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.sizeExpr.visit(this,null);
        if(!expr.sizeExpr.type.equals(INT)) reporter.reportError("*** Type Error at line " + expr.sizeExpr.posn.start + ": INT type expected");
        expr.type = new ArrayType((TypeDenoter)expr.eltType.visit(this,null), null);
        return null;
    }


    /*
     *
     * Reference
     *
     */
    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        return null;
    }

    @Override
    public Object visitQRef(QualRef ref, Object arg) {
        return null;
    }


    /*
     *
     * Terminal
     *
     */
    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        return null;
    }

    @Override
    public Object visitOperator(Operator op, Object arg) {
        return null;
    }

    @Override
    public Object visitIntLiteral(IntLiteral num, Object arg) {
        return null;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
        return null;
    }

    @Override
    public Object visitNullLiteral(NullLiteral literal, Object arg) {
        return null;
    }

    private void reportTypeIncompatibleError(SourcePosition posn) {
        reporter.reportError("*** Type Error at line " + posn.start + ": Incompatible types");
    }
}
