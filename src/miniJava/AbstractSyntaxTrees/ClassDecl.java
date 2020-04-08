/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import  miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;

public class ClassDecl extends Declaration {

    public int nonStaticFieldCount;
    public ClassDecl(String cn, FieldDeclList fdl, MethodDeclList mdl, SourcePosition posn) {
        super(cn, new ClassType(new Identifier(new Token(TokenKind.ID, cn,null)), null), posn);
        fieldDeclList = fdl;
        methodDeclList = mdl;
    }

    public <A,R> R visit(Visitor<A, R> v, A o) {
      return v.visitClassDecl(this, o);
  }

    public FieldDeclList fieldDeclList;
    public MethodDeclList methodDeclList;

    public MemberDecl hasMember(String name, boolean expectStatic, boolean expectPublic) {
        for (FieldDecl fd : fieldDeclList)
          if (name.equals(fd.name)) {
             if (expectStatic && !fd.isStatic) continue;
             if (expectPublic && fd.isPrivate) continue;
             return fd;
          }

        for (MethodDecl md : methodDeclList){
          if (name.equals(md.name)) {
              if (expectStatic && !md.isStatic) continue;
              if (expectPublic && md.isPrivate) continue;
              return md;
          }
      }

      return null;
    }
}
