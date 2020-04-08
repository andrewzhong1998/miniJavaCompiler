/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class ClassType extends TypeDenoter
{
    public ClassType(Identifier cn, SourcePosition posn){
        super(TypeKind.CLASS, posn);
        className = cn;
    }
            
    public <A,R> R visit(Visitor<A,R> v, A o) {
        return v.visitClassType(this, o);
    }

    public Identifier className;

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        TypeDenoter type = (TypeDenoter) obj;
        if (type.typeKind == TypeKind.UNSUPPORTED || this.typeKind == TypeKind.UNSUPPORTED) {
            return false;
        }

        if (type.typeKind == TypeKind.ERROR || this.typeKind == TypeKind.ERROR) {
            return true;
        }

        if (type.typeKind == TypeKind.ANY || this.typeKind == TypeKind.ANY) {
            return true;
        }

        if (type instanceof ClassType) {
            return ((ClassType) type).className.spelling.equals(this.className.spelling);
        }

        return false;
    }

}
