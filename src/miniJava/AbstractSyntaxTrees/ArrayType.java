/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */

package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class ArrayType extends TypeDenoter {
	public TypeDenoter eltType;

	public ArrayType(TypeDenoter eltType, SourcePosition posn){
		super(TypeKind.ARRAY, posn);
		this.eltType = eltType;
	}

	public <A,R> R visit(Visitor<A,R> v, A o) {
	        return v.visitArrayType(this, o);
	    }

	public boolean equals(Object obj){
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

		if (type instanceof ArrayType) {
			return ((ArrayType) type).eltType.equals(this.eltType);
		}

		return false;
	}

}

