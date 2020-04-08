package miniJava.SyntacticAnalyzer;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.ErrorReporter;

public class Parser {
    private Scanner scanner;
    private ErrorReporter reporter;
    private Token token;
    private boolean trace = false;
    private SourcePosition prevPosn;

    public Parser(Scanner scanner, ErrorReporter reporter) {
        this.scanner = scanner;
        this.reporter = reporter;
        this.prevPosn = new SourcePosition();
    }

    private void startPosn(SourcePosition position) {
        position.start = token.posn.start;
    }

    private void finishPosn(SourcePosition position) {
        position.finish = prevPosn.finish;
    }

    // (ClassDeclaration)* eot
    public Package parse() {
        ClassDeclList cdl = new ClassDeclList();
        token = scanner.scan();
        SourcePosition posn = new SourcePosition();
        startPosn(posn);
        try {
            while(token.kind != TokenKind.EOT) cdl.add(parseClass());
            accept(TokenKind.EOT);
        }
        catch (SyntaxError e) { }
        finishPosn(posn);
        return new Package(cdl, posn);
    }

    // class id { ( FieldDeclaration | MethodDeclaration )* }
    public ClassDecl parseClass() throws  SyntaxError {
        SourcePosition classPosn = new SourcePosition();
        startPosn(classPosn);

        accept(TokenKind.CLASS);
        String class_name = token.spelling;
        accept(TokenKind.ID);
        accept(TokenKind.LC);

        FieldDeclList fdl = new FieldDeclList();
        MethodDeclList mdl = new MethodDeclList();
        while(isStartersDeclaration(token.kind)) {
            SourcePosition methodPosition = new SourcePosition();
            SourcePosition fieldPosition = new SourcePosition();
            startPosn(methodPosition);
            startPosn(fieldPosition);
            boolean isPrivate = parseVisibility();
            boolean isStatic = parseAccess();
            TypeDenoter mt;
            String name;
            // ( Type | void ) id ( ParameterList? ) {Statement*}
            // Type id ;
            switch (token.kind) {
                case VOID:
                    mt = new BaseType(TypeKind.VOID,token.posn);
                    acceptIt();
                    name = token.spelling;
                    accept(TokenKind.ID);
                    break;

                default:
                    mt = parseType();
                    name = token.spelling;
                    accept(TokenKind.ID);
                    if(token.kind == TokenKind.SC) {
                        acceptIt();
                        finishPosn(fieldPosition);
                        fdl.add(new FieldDecl(isPrivate, isStatic, mt, name, fieldPosition));
                        continue;
                    }
            }
            finishPosn(fieldPosition);
            accept(TokenKind.LP);
            ParameterDeclList pdl = parseParameterList();
            accept(TokenKind.RP);
            accept(TokenKind.LC);
            StatementList sl = new StatementList();
            while(isStartersStatement(token.kind)) sl.add(parseStatement());
            accept(TokenKind.RC);
            finishPosn(methodPosition);
            mdl.add(new MethodDecl(new FieldDecl(isPrivate, isStatic, mt, name, fieldPosition), pdl, sl, methodPosition));
        }
        accept(TokenKind.RC);
        finishPosn(classPosn);
        return new ClassDecl(class_name, fdl, mdl, classPosn);
    }


    // ( public | private )?
    private boolean parseVisibility() throws SyntaxError {
        if (token.kind == TokenKind.PUBLIC) {
            acceptIt();
            return false;
        }
        if (token.kind == TokenKind.PRIVATE) {
            acceptIt();
            return true;
        }
        return false;
    }

    // static ?
    private boolean parseAccess() throws SyntaxError {
        if(token.kind == TokenKind.STATIC) {
            acceptIt();
            return true;
        }
        return false;
    }

    // int | boolean | id | ( int | id ) []
    private TypeDenoter parseType() throws SyntaxError {
        switch (token.kind) {
            case BOOLEAN:
                acceptIt();
                return new BaseType(TypeKind.BOOLEAN, new SourcePosition(prevPosn));

            case INT:
                SourcePosition intArrayPosn = new SourcePosition();
                startPosn(intArrayPosn);
                acceptIt();
                SourcePosition intPosn = new SourcePosition(prevPosn);
                if (token.kind == TokenKind.LB) {
                    acceptIt();
                    accept(TokenKind.RB);
                    finishPosn(intArrayPosn);
                    return new ArrayType(new BaseType(TypeKind.INT, intPosn), intArrayPosn);
                }
                return new BaseType(TypeKind.INT, intPosn);

            default:
                Token first = token;
                SourcePosition classArrrayPosn = new SourcePosition();
                startPosn(classArrrayPosn);
                accept(TokenKind.ID);
                SourcePosition classPosn = new SourcePosition(prevPosn);
                if (token.kind == TokenKind.LB) {
                    acceptIt();
                    accept(TokenKind.RB);
                    finishPosn(classArrrayPosn);
                    return new ArrayType(new ClassType(new Identifier(first), classPosn), classArrrayPosn);
                }
                return new ClassType(new Identifier(first), classPosn);
        }
    }

    // Type id ( , Type id )*
    private ParameterDeclList parseParameterList() throws  SyntaxError {
        ParameterDeclList pdl = new ParameterDeclList();
        if(token.kind == TokenKind.RP) return pdl;

        SourcePosition paramPosn = new SourcePosition();
        startPosn(paramPosn);
        TypeDenoter paramType = parseType();
        String paramName = token.spelling;
        accept(TokenKind.ID);
        finishPosn(paramPosn);

        pdl.add(new ParameterDecl(paramType,paramName,paramPosn));

        while(token.kind == TokenKind.COM) {
            acceptIt();

            paramPosn = new SourcePosition();
            startPosn(paramPosn);
            paramType = parseType();
            paramName = token.spelling;
            accept(TokenKind.ID);
            finishPosn(paramPosn);

            pdl.add(new ParameterDecl(paramType,paramName,paramPosn));
        }
        return pdl;
    }

    // Expression ( , Expression )*
    private ExprList parseArgumentList() throws  SyntaxError {
        ExprList el = new ExprList();
        el.add(parseExpression());
        while(token.kind == TokenKind.COM) {
            acceptIt();
            el.add(parseExpression());
        }
        return el;
    }

    /*
    { Statement* }
    | Type id = Expression ;
    | ( Reference | IxReference ) = Expression ;
    | Reference ( ArgumentList? ) ;
    | return Expression? ;
    | if ( Expression ) Statement (else Statement)?
    | while ( Expression ) Statement
     */
    private Statement parseStatement() throws  SyntaxError {
        SourcePosition stmtPosn = new SourcePosition();
        switch (token.kind) {
            case WHILE:
                startPosn(stmtPosn);
                acceptIt();
                accept(TokenKind.LP);
                Expression whileCond = parseExpression();
                accept(TokenKind.RP);
                Statement whileStat = parseStatement();
                finishPosn(stmtPosn);
                return new WhileStmt(whileCond, whileStat, stmtPosn);

            case IF:
                startPosn(stmtPosn);
                acceptIt();
                accept(TokenKind.LP);
                Expression ifCond = parseExpression();
                accept(TokenKind.RP);
                Statement thenStat = parseStatement();
                if(token.kind == TokenKind.ELSE) {
                    acceptIt();
                    Statement elseStat = parseStatement();
                    finishPosn(stmtPosn);
                    return new IfStmt(ifCond, thenStat, elseStat, stmtPosn);
                }
                finishPosn(stmtPosn);
                return new IfStmt(ifCond, thenStat, stmtPosn);

            case RETURN:
                startPosn(stmtPosn);
                acceptIt();
                Expression returnObject = null;
                if(token.kind != TokenKind.SC) returnObject = parseExpression();
                accept(TokenKind.SC);
                finishPosn(stmtPosn);
                return new ReturnStmt(returnObject, stmtPosn);


            case LC:
                startPosn(stmtPosn);
                accept(TokenKind.LC);
                StatementList sl = new StatementList();
                while(token.kind != TokenKind.RC) sl.add(parseStatement());
                accept(TokenKind.RC);
                finishPosn(stmtPosn);
                return new BlockStmt(sl, stmtPosn);


            default:
                startPosn(stmtPosn);
                if(token.kind == TokenKind.THIS) {
                    Reference r = parseReference();
                    Expression i = null;
                    if(token.kind == TokenKind.LP) {
                        acceptIt();
                        ExprList el = new ExprList();
                        if(token.kind != TokenKind.RP) {
                            el = parseArgumentList();
                        }
                        accept(TokenKind.RP);
                        accept(TokenKind.SC);
                        finishPosn(stmtPosn);
                        return new CallStmt(r, el, stmtPosn);
                    }
                    if(token.kind == TokenKind.LB) {
                        acceptIt();
                        i = parseExpression();
                        accept(TokenKind.RB);
                    }
                    accept(TokenKind.IS);
                    Expression e = parseExpression();
                    accept(TokenKind.SC);
                    finishPosn(stmtPosn);
                    if (i==null) return new AssignStmt(r, e, stmtPosn);
                    return new IxAssignStmt(r, i, e, stmtPosn);
                }
                else if(token.kind == TokenKind.INT || token.kind == TokenKind.BOOLEAN) {
                    SourcePosition varDeclPosn = new SourcePosition();
                    startPosn(varDeclPosn);
                    TypeDenoter t = parseType();
                    String name = token.spelling;
                    accept(TokenKind.ID);
                    finishPosn(varDeclPosn);
                    accept(TokenKind.IS);
                    Expression expr = parseExpression();
                    accept(TokenKind.SC);
                    finishPosn(stmtPosn);
                    return new VarDeclStmt(new VarDecl(t, name, varDeclPosn), expr, stmtPosn);
                }
                else {
                    SourcePosition classArrayPosn = new SourcePosition();
                    SourcePosition varDeclPosn = new SourcePosition();
                    startPosn(classArrayPosn);
                    startPosn(varDeclPosn);
                    Token first = token;
                    accept(TokenKind.ID);
                    SourcePosition classPosn = new SourcePosition(prevPosn);
                    // id[] id = Expression
                    // id[Expression] = Expression;
                    if (token.kind == TokenKind.LB) {
                        acceptIt();
                        if (token.kind == TokenKind.RB) {
                            acceptIt();
                            finishPosn(classArrayPosn);
                            String name = token.spelling;
                            accept(TokenKind.ID);
                            finishPosn(varDeclPosn);
                            accept(TokenKind.IS);
                            Expression expr = parseExpression();
                            accept(TokenKind.SC);
                            finishPosn(stmtPosn);
                            return new VarDeclStmt(new VarDecl(new ArrayType(new ClassType(new Identifier(first), classPosn), classArrayPosn), name, varDeclPosn), expr, stmtPosn);
                        }
                        else {
                            Expression i = parseExpression();
                            accept(TokenKind.RB);
                            accept(TokenKind.IS);
                            Expression e = parseExpression();
                            accept(TokenKind.SC);
                            finishPosn(stmtPosn);
                            return new IxAssignStmt(new IdRef(new Identifier(first), classPosn), i, e, stmtPosn);
                        }
                    }
                    // check id id == Expression
                    else if (token.kind == TokenKind.ID) {
                        String name = token.spelling;
                        acceptIt();
                        finishPosn(varDeclPosn);
                        accept(TokenKind.IS);
                        Expression expr = parseExpression();
                        accept(TokenKind.SC);
                        finishPosn(stmtPosn);
                        return new VarDeclStmt(new VarDecl(new ClassType(new Identifier(first), classPosn), name, varDeclPosn), expr, stmtPosn);
                    }
                    // id(.id)*([Expression])? = Expression;
                    // id(.id)*(ArgumentList?);
                    else {
                        Reference r = new IdRef(new Identifier(first), classPosn);
                        Expression i = null;
                        while(token.kind == TokenKind.DOT) {
                            acceptIt();
                            Token tmp = token;
                            accept(TokenKind.ID);
                            r = new QualRef(r, new Identifier(tmp), new SourcePosition(classPosn.start,prevPosn.finish));
                        }
                        if(token.kind == TokenKind.LP) {
                            acceptIt();
                            ExprList el = new ExprList();
                            if(token.kind != TokenKind.RP) {
                                el = parseArgumentList();
                            }
                            accept(TokenKind.RP);
                            accept(TokenKind.SC);
                            finishPosn(stmtPosn);
                            return new CallStmt(r, el, stmtPosn);
                        }
                        if(token.kind == TokenKind.LB) {
                            acceptIt();
                            i = parseExpression();
                            accept(TokenKind.RB);
                        }
                        accept(TokenKind.IS);
                        Expression e = parseExpression();
                        accept(TokenKind.SC);
                        finishPosn(stmtPosn);
                        if (i==null) return new AssignStmt(r, e, stmtPosn);
                        return new IxAssignStmt(r, i, e, stmtPosn);
                    }
                }
        }
    }

    private Reference parseReference() throws SyntaxError {
        Reference r;
        SourcePosition qualStartPosn = new SourcePosition(token.posn);
        if(token.kind == TokenKind.ID) {
            r = new IdRef(new Identifier(token), new SourcePosition(token.posn));
            acceptIt();
        }
        else {
            r = new ThisRef(new SourcePosition(token.posn));
            accept(TokenKind.THIS);
        }
        while(token.kind == TokenKind.DOT) {
            acceptIt();
            r = new QualRef(r, new Identifier(token), new SourcePosition(qualStartPosn.start,token.posn.finish));
            accept(TokenKind.ID);
        }
        return r;
    }

    // Expression ::= D (|| D)*
    // D ::= C (&& C)*
    // C ::= E ((==|!=)E)*
    // E ::= R ((<=|<|>|>=)R)*
    // R ::= A ((+|-)A)*
    // A ::= M ((*|/)M)*
    // M ::= PrimExpr
    private Expression parseExpression() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseD();
        while(token.kind == TokenKind.OR) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseD(), exprPosn);
        }
        return expr;
    }

    private Expression parseD() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseC();
        while(token.kind == TokenKind.AND) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseC(), exprPosn);
        }
        return expr;
    }

    private Expression parseC() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseE();
        while(token.kind == TokenKind.EQ || token.kind == TokenKind.NE) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseE(), exprPosn);
        }
        return expr;
    }

    private Expression parseE() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseR();
        while(token.kind == TokenKind.LT || token.kind == TokenKind.LE || token.kind == TokenKind.GT || token.kind == TokenKind.GE) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseR(), exprPosn);
        }
        return expr;
    }

    private Expression parseR() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseA();
        while(token.kind == TokenKind.ADD || token.kind == TokenKind.MI) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseA(), exprPosn);
        }
        return expr;
    }

    private Expression parseA() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        Expression expr = parseU();
        while(token.kind == TokenKind.MUL || token.kind == TokenKind.DIV) {
            Operator oper = new Operator(token);
            acceptIt();
            finishPosn(exprPosn);
            expr = new BinaryExpr(oper, expr, parseU(), exprPosn);
        }
        return expr;
    }

    /*
     Reference
    | IxReference
    | Reference ( ArgumentList? )
    | unop Expression
    | ( Expression )
    | num | true | false
    | new ( id () | int [ Expression ] | id [ Expression ] )
     */
    private Expression parseU() throws SyntaxError {
        SourcePosition exprPosn = new SourcePosition();
        startPosn(exprPosn);
        switch (token.kind) {
            // null
            case NULL:
                NullLiteral nullLiteral = new NullLiteral(token);
                acceptIt();
                finishPosn(exprPosn);
                return new LiteralExpr(nullLiteral, exprPosn);

            // num | true | false
            case NUM:
                Terminal intLit = new IntLiteral(token);
                acceptIt();
                finishPosn(exprPosn);
                return new LiteralExpr(intLit, exprPosn);

            case TRUE: case FALSE:
                Terminal booLit = new BooleanLiteral(token);
                acceptIt();
                finishPosn(exprPosn);
                return new LiteralExpr(booLit, exprPosn);

            // new ( id () | int [ Expression ] | id [ Expression ] )
            case NEW:
                acceptIt();
                if(token.kind == TokenKind.INT) {
                    acceptIt();
                    SourcePosition intPosn = new SourcePosition(prevPosn);
                    accept(TokenKind.LB);
                    Expression e = parseExpression();
                    accept(TokenKind.RB);
                    finishPosn(exprPosn);
                    return new NewArrayExpr(new BaseType(TypeKind.INT, intPosn), e, exprPosn);
                }
                else {
                    Token first = token;
                    accept(TokenKind.ID);
                    SourcePosition classPosn = new SourcePosition(prevPosn);
                    if(token.kind == TokenKind.LP) {
                        acceptIt();
                        accept(TokenKind.RP);
                        finishPosn(exprPosn);
                        return new NewObjectExpr(new ClassType(new Identifier(first), classPosn), exprPosn);
                    }
                    else {
                        accept(TokenKind.LB);
                        Expression e = parseExpression();
                        accept(TokenKind.RB);
                        finishPosn(exprPosn);
                        return new NewArrayExpr(new ClassType(new Identifier(first), classPosn), e, exprPosn);
                    }
                }

            // unop Expression
            case NOT: case MI:
                Operator oper = new Operator(token);
                acceptIt();
                finishPosn(exprPosn);
                return new UnaryExpr(oper, parseU(), exprPosn);

            // Reference | Reference[Expression] | Reference ( ArgumentList? )
            case ID: case THIS:
                Reference r = parseReference();
                if(token.kind == TokenKind.LB) {
                    acceptIt();
                    Expression e = parseExpression();
                    accept(TokenKind.RB);
                    finishPosn(exprPosn);
                    return new IxExpr(r, e, exprPosn);
                }
                else if(token.kind == TokenKind.LP) {
                    acceptIt();
                    ExprList el = new ExprList();
                    if(token.kind != TokenKind.RP) {
                        el = parseArgumentList();
                    }
                    accept(TokenKind.RP);
                    finishPosn(exprPosn);
                    return new CallExpr(r, el, exprPosn);
                }
                finishPosn(exprPosn);
                return new RefExpr(r, exprPosn);

            // ( Expression )
            default:
                accept(TokenKind.LP);
                Expression expr = parseExpression();
                accept(TokenKind.RP);
                return expr;
        }
    }

    private void acceptIt() throws SyntaxError {
        accept(token.kind);
    }

    private void accept(TokenKind expectedTokenKind) throws SyntaxError {
        if (token.kind == expectedTokenKind) {
            if (trace) pTrace(true);
            prevPosn = token.posn;
            token = scanner.scan();
        }
        else {
            if (trace) pTrace(false);
            parseError("expecting '" + expectedTokenKind +
                    "' but found '" + token.kind + "' at '" + token.spelling + "'");
        }
    }

    private boolean isStartersDeclaration(TokenKind tokenKind) {
        return tokenKind == TokenKind.PUBLIC || tokenKind == TokenKind.PRIVATE || tokenKind == TokenKind.STATIC || tokenKind == TokenKind.VOID || tokenKind == TokenKind.INT || tokenKind == TokenKind.BOOLEAN || tokenKind == TokenKind.ID;
    }

    private boolean isStartersStatement(TokenKind tokenKind) {
        return tokenKind == TokenKind.LC || tokenKind == TokenKind.INT || tokenKind == TokenKind.BOOLEAN || tokenKind == TokenKind.ID || tokenKind == TokenKind.THIS || tokenKind == TokenKind.RETURN || tokenKind == TokenKind.IF || tokenKind == TokenKind.WHILE;
    }

    class SyntaxError extends Error {
        private static final long serialVersionUID = 1L;
    }

    private void parseError(String e) throws SyntaxError {
        reporter.reportError("Parse error: " + e);
        throw new SyntaxError();
    }

    private void pTrace(boolean accept) {
        StackTraceElement [] stl = Thread.currentThread().getStackTrace();
        for (int i = stl.length - 1; i > 0 ; i--) {
            if(stl[i].toString().contains("parse"))
                System.out.println(stl[i]);
        }
        if(accept) {
            System.out.println("accepting: " + token.kind + " (\"" + token.spelling + "\")");
            System.out.println();
        }
    }
}