package miniJava.ContextualAnalyzer;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;

import java.util.HashSet;

public class IdChecker implements Visitor<Object, Object> {
    private ErrorReporter reporter;
    private IdentificationTable idTable;
    private boolean inStaticContext;
    private ClassDecl currClass;
    private HashSet<String> localVariables;
    private String currDeclaringVar;
    public static FieldDecl ARRAY_LENGTH = new FieldDecl(false,false,new BaseType(TypeKind.INT,null),"length",null);
    public static MethodDecl PRINTLN;

    public IdChecker(ErrorReporter reporter){
        this.reporter = reporter;
        this.idTable = new IdentificationTable();
        this.inStaticContext = false;
        this.currDeclaringVar = "";
    }

    public void check(AST ast) {
        loadStandardEnvironment();
        ast.visit(this,null);
    }

    public void loadStandardEnvironment() {
        // String
        ClassDecl String = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), new SourcePosition());
        idTable.enter("String", String);

        // _PrintStream
        MethodDeclList printStreamMDL = new MethodDeclList();
        FieldDecl printlnFD = new FieldDecl(false, false, new BaseType(TypeKind.VOID, new SourcePosition()), "println",new SourcePosition());
        ParameterDeclList printlnPDL = new ParameterDeclList();
        printlnPDL.add(new ParameterDecl(new BaseType(TypeKind.INT, new SourcePosition()), "n", new SourcePosition()));
        PRINTLN = new MethodDecl(printlnFD, printlnPDL, new StatementList(), new SourcePosition());

        printStreamMDL.add(PRINTLN);
        ClassDecl _PrintStream = new ClassDecl("_PrintStream", new FieldDeclList(), printStreamMDL, new SourcePosition());
        idTable.enter("_PrintStream", _PrintStream);

        // System
        FieldDeclList systemFDL = new FieldDeclList();
        FieldDecl outFD = new FieldDecl(
                false,
                true,
                new ClassType(new Identifier(new Token(TokenKind.ID,"_PrintStream",new SourcePosition())), null),
                "out",
                new SourcePosition());
        systemFDL.add(outFD);
        ClassDecl System = new ClassDecl("System", systemFDL, new MethodDeclList(), new SourcePosition());
        idTable.enter("System", System);
    }

    @Override
    public Object visitPackage(Package prog, Object arg) {
        ClassDeclList cdl = prog.classDeclList;
        for (ClassDecl cd : cdl) if(!idTable.enter(cd.name, cd)) reporter.reportError("*** Identification Error at line " + cd.posn.start + ": Duplicate declaration of " + cd.name);
        for (ClassDecl cd : cdl) {
            idTable.openScope();
            currClass = cd;
            cd.visit(this, null);
            idTable.closeScope();
        }

        return null;
    }

    /*
     *
     * Decl
     *
     *
     */
    @Override
    public Object visitClassDecl(ClassDecl cd, Object arg) {
        for (FieldDecl fd : cd.fieldDeclList) if (!idTable.enter(fd.name, fd)) reporter.reportError("*** Identification Error at line " + fd.posn.start + ": Duplicate declaration of " + fd.name);
        for (MethodDecl md : cd.methodDeclList) if (!idTable.enter(md.name, md)) reporter.reportError("*** Identification Error at line " + md.posn.start + ": Duplicate declaration of " + md.name);
        for (FieldDecl fd : cd.fieldDeclList) fd.visit(this, null);
        for (MethodDecl md : cd.methodDeclList) md.visit(this,null);
        return null;
    }

    @Override
    public Object visitFieldDecl(FieldDecl fd, Object arg) {
        fd.type.visit(this, null);
        return null;
    }

    @Override
    public Object visitMethodDecl(MethodDecl md, Object arg) {
        md.type.visit(this,null);
        this.localVariables = new HashSet<>();
        idTable.openScope();
        inStaticContext = md.isStatic;
        for (ParameterDecl pd : md.parameterDeclList) pd.visit(this, 0);
        for (Statement stmt : md.statementList) stmt.visit(this, 0);
        idTable.closeScope();
        return null;
    }

    @Override
    public Object visitParameterDecl(ParameterDecl pd, Object arg) {
        pd.type.visit(this, null);
        if (localVariables.contains(pd.name)) reporter.reportError("*** Identification Error at line " + pd.posn.start + ": Duplicate declaration of " + pd.name);
        else {
            localVariables.add(pd.name);
            idTable.enter(pd.name, pd);
        }
        return null;
    }

    @Override
    public Object visitVarDecl(VarDecl decl, Object arg) {
        decl.type.visit(this, null);
        if (localVariables.contains(decl.name)) reporter.reportError("*** Identification Error at line " + decl.posn.start + ": Duplicate declaration of " + decl.name);
        else {
            localVariables.add(decl.name);
            idTable.enter(decl.name, decl);
        }
        return null;
    }

    /*
     *
     * Type
     *
     *
     */
    @Override
    public Object visitBaseType(BaseType type, Object arg) {
        return null;
    }

    @Override
    public Object visitClassType(ClassType type, Object arg) {
        ClassDecl cd = (ClassDecl) idTable.retrieveClass(type.className.spelling);
        if (cd == null) reporter.reportError("*** Identification Error at line " + type.posn.start + ": Cannot resolve symbol \'"+type.className.spelling+"\'");
        else type.className.decl = cd;
        if (type.className.spelling.equals("String")) return new BaseType(TypeKind.UNSUPPORTED, new SourcePosition(type.posn));
        return null;
    }

    @Override
    public Object visitArrayType(ArrayType type, Object arg) {
        type.eltType.visit(this, null);
        return null;
    }

    /*
     *
     * Statement
     *
     *
     */
    @Override
    public Object visitBlockStmt(BlockStmt stmt, Object arg) {
        idTable.openScope();
        for (Statement st : stmt.sl) st.visit(this, null);
        idTable.closeScope();
        return null;
    }

    @Override
    public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
        stmt.varDecl.visit(this, null);
        currDeclaringVar = stmt.varDecl.name;
        stmt.initExp.visit(this, null);
        currDeclaringVar = "";
        return null;
    }

    @Override
    public Object visitAssignStmt(AssignStmt stmt, Object arg) {
        stmt.val.visit(this, null);
        stmt.ref.visit(this, 2);
        if (stmt.ref instanceof ThisRef) reporter.reportError("*** Identification Error at line " + stmt.posn.start + ": Variable expected");
        if (stmt.ref.decl == ARRAY_LENGTH) reporter.reportError("*** Identification Error at line " + stmt.posn.start + ": Cannot assign value to length field of array type");
        return null;
    }

    @Override
    public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
        if (stmt.ref instanceof ThisRef) reporter.reportError("*** Identification Error at line " + stmt.posn.start + ": Variable expected");
        else stmt.ref.visit(this, 2);
        stmt.ix.visit(this, null);
        stmt.exp.visit(this, null);
        return null;
    }

    @Override
    public Object visitCallStmt(CallStmt stmt, Object arg) {
        stmt.methodRef.visit(this, 1);
        if(stmt.methodRef.decl != null) {
            if (!(stmt.methodRef.decl instanceof MethodDecl))
                reporter.reportError("*** Identification Error at line " + stmt.posn.start + ": Method expected");
            else {
                MethodDecl md = (MethodDecl) stmt.methodRef.decl;
                if (md.parameterDeclList.size() != stmt.argList.size())
                    reporter.reportError("*** Identification Error at line " + stmt.posn.start + ": Number of arguments not agree with number of parameters");
                else for (Expression expr : stmt.argList) expr.visit(this, null);
            }
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
        if (stmt.returnExpr != null) stmt.returnExpr.visit(this, 0);
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt stmt, Object arg) {
        stmt.cond.visit(this, null);
        if (stmt.thenStmt instanceof VarDeclStmt) reporter.reportError("*** Identification Error at line " + stmt.thenStmt.posn.start + ": Solitary declaration not allowed in conditional");
        else stmt.thenStmt.visit(this, null);
        if (stmt.elseStmt != null) {
            if (stmt.elseStmt instanceof VarDeclStmt) reporter.reportError("*** Identification Error at line " + stmt.elseStmt.posn.start + ": Solitary declaration not allowed in conditional");
            else stmt.elseStmt.visit(this, null);
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt stmt, Object arg) {
        stmt.cond.visit(this, null);
        if (stmt.body instanceof VarDeclStmt) reporter.reportError("*** Identification Error at line " + stmt.body.posn.start + ": Solitary declaration not allowed in conditional");
        else stmt.body.visit(this, null);
        return null;
    }

    /*
     *
     * Expression
     *
     *
     */
    @Override
    public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
        expr.expr.visit(this, null);
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
        expr.left.visit(this, null);
        expr.right.visit(this, null);
        return null;
    }

    @Override
    public Object visitRefExpr(RefExpr expr, Object arg) {
        expr.ref.visit(this, 2);
        return null;
    }

    @Override
    public Object visitIxExpr(IxExpr expr, Object arg) {
        expr.ref.visit(this,2);
        expr.ixExpr.visit(this,null);
        return null;
    }

    @Override
    public Object visitCallExpr(CallExpr expr, Object arg) {
        expr.functionRef.visit(this, 1);
        if(expr.functionRef.decl != null) {
            if (!(expr.functionRef.decl instanceof MethodDecl)) reporter.reportError("*** Identification Error at line " + expr.posn.start + ": Method expected");
            else {
                MethodDecl md = (MethodDecl) expr.functionRef.decl;
                if (md.parameterDeclList.size() != expr.argList.size()) reporter.reportError("*** Identification Error at line " + expr.posn.start + ": Number of arguments not agree with number of parameters");
                else for (Expression e : expr.argList) e.visit(this, null);
            }
        }
        return null;
    }

    @Override
    public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
        expr.lit.visit(this,null);
        return null;
    }

    @Override
    public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
        expr.classtype.visit(this,null);
        return null;
    }

    @Override
    public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
        expr.eltType.visit(this,null);
        expr.sizeExpr.visit(this,null);
        return null;
    }


    /*
     *
     * Reference
     *
     *
     */
    @Override
    public Object visitThisRef(ThisRef ref, Object arg) {
        if (inStaticContext) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": \'this\' cannot be referenced in static context");
        ref.decl = currClass;
        return null;
    }

    @Override
    public Object visitIdRef(IdRef ref, Object arg) {
        ref.id.visit(this, arg);
        ref.decl = ref.id.decl;
        if(ref.decl != null) {
            if (ref.id.spelling.equals(currDeclaringVar)) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": \'" + currDeclaringVar + "\' may not have been initialized");
            else if (inStaticContext && ref.decl instanceof  MemberDecl && !((MemberDecl)ref.decl).isStatic) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Non-static member cannot be referenced in static context");
        }
        return null;
    }

    @Override
    /*
     QRef is guaranteed to have decl as an instance of MemberDecl
     The BaseRef of QRef should be one of {ClassDecl, ParamDecl, VarDecl, FieldDecl}, cannot be MethodDecl.
     */
    public Object visitQRef(QualRef ref, Object arg) {
        ref.ref.visit(this, 0);
        Declaration baseDecl = ref.ref.decl;
        if (baseDecl == null) return null;
        // MethodDecl => Error
        else if (baseDecl instanceof MethodDecl) {
            reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot have a method name at the beginning of a QRef");
        }
        // ClassDecl => CLASS.member
        else if (baseDecl instanceof ClassDecl && !(ref.ref instanceof ThisRef)) {
            boolean sameClass = baseDecl == currClass;
            String memberName = ref.id.spelling;
            ClassDecl baseClass = (ClassDecl) baseDecl;
            if (sameClass) {
                Declaration md = currClass.hasMember(memberName, true, false);
                if (md == null) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot resolve member \'" + ref.id.spelling + "\' in class \'" + currClass.name + "\' because it is either not defined within that class or is non-static");
                else ref.decl = md;
            }
            else {
                Declaration md = baseClass.hasMember(memberName, true, true);
                if (md == null) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot resolve member \'" + ref.id.spelling + "\' in class \'" + baseDecl.name + "\' because it is either 1) not defined within that class, 2) it is non-static, or 3) it is not visible to the current class");
                else ref.decl = md;
            }
        }
        // ParamDecl/VarDecl/FieldDecl => TypeKind is CLASS? => class.member
        else {
            // TypeKind is Class
            if (baseDecl.type.typeKind == TypeKind.CLASS) {
                String className = ((ClassType) baseDecl.type).className.spelling;
                String memberName = ref.id.spelling;
                ClassDecl baseClass = (ClassDecl) idTable.retrieveClass(className);
                boolean sameClass = baseClass == currClass;
                if (sameClass) {
                    Declaration md = currClass.hasMember(memberName, false, false);
                    if (md == null) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot resolve member \'" + ref.id.spelling + "\' in class \'" + currClass.name + "\' because it is not defined within that class");
                    else ref.decl = md;
                }
                else {
                    Declaration md = baseClass.hasMember(memberName, false, true);
                    if (md == null) reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot resolve member \'" + ref.id.spelling + "\' in class \'" + baseDecl.name + "\' because it is either not defined within that class or is not visible to the current class");
                    else ref.decl = md;
                }
            }
            // TypeKind is Array
            else if (baseDecl.type.typeKind == TypeKind.ARRAY) {
                if(ref.id.spelling.equals("length")){
                    ref.decl = ARRAY_LENGTH;
                }
                else {
                    reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Can only reference length field of array type");
                }
            }
            // Otherwise
            else {
                reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Cannot reference members of primitive type");
            }
        }
        if(ref.decl == null) return null;
        int flag = 0;
        if (arg != null) flag = (Integer) arg;
        if (flag == 1 && ref.decl instanceof FieldDecl) {
            ref.decl = null;
            reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Expecting a method name");
        }
        if (flag == 2 && ref.decl instanceof MethodDecl) {
            ref.decl = null;
            reporter.reportError("*** Identification Error at line " + ref.posn.start + ": Expecting a field name");
        }
        return null;
    }


    /*
     *
     * Terminal
     *
     *
     */
    @Override
    public Object visitIdentifier(Identifier id, Object arg) {
        int flag = 0;
        if (arg != null) flag = (Integer) arg;

        // Find methods
        if (flag == 1) {
            Declaration decl = idTable.retrieveMethod(id.spelling);
            if (decl == null) reporter.reportError("*** Identification Error at line " + id.posn.start + ": Expecting a method name");
            else id.decl = decl;
            return id.decl;
        }

        // Find non-ClassDecl and non-MethodDecl
        if (flag == 2) {
            Declaration decl = idTable.retrieveFPV(id.spelling);
            if (decl == null) reporter.reportError("*** Identification Error at line " + id.posn.start + ": Expecting a field/parameter/variable name");
            else id.decl = decl;
            return id.decl;
        }

        // Default
        Declaration decl = idTable.retrieve(id.spelling);
        id.decl = decl;
        if (decl == null) reporter.reportError("*** Identification Error at line " + id.posn.start + ": Cannot resolve symbol \'" + id.spelling + "\'");
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
}
