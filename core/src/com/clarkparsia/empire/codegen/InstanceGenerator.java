/*
 * Copyright (c) 2009-2010 Clark & Parsia, LLC. <http://www.clarkparsia.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.clarkparsia.empire.codegen;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewConstructor;
import javassist.CtField;
import javassist.CtNewMethod;
import javassist.NotFoundException;

import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Method;

import com.clarkparsia.empire.SupportsRdfId;

/**
 * <p>Generate implementations of interfaces at runtime via bytecode manipulation.</p>
 *
 * @author Michael Grove
 */
public class InstanceGenerator {

	/**
	 * <p>Given a bean-style interface, generate an instance of the interface by implementing getters and setters for each
	 * property.  It will also add implementations to support the {@link SupportsRdfId} interface and generate simple,
	 * default equals, toString and hashCode methods.</p>
	 *
	 * <p>If there are other non-bean style (getter and/or setter's for properties) methods on the interface, this will
	 * likely fail to generate the instance.</p>
	 * @param theInterface the interface to build an instance of
	 * @param <T> the type of the interface
	 * @return New dynamically generated bytecode of a class that implements the given interface.
	 * @throws Exception if there is an error while generating the bytecode of the new class.
	 */
	public static <T> Class<T> generateInstanceClass(Class<T> theInterface) throws Exception {
		ClassPool aPool = ClassPool.getDefault();
		CtClass aInterface = aPool.get(theInterface.getName());


		String aName = aInterface.getPackageName()+ ".impl." + aInterface.getSimpleName() + "Impl";
		CtClass aClass = null;
		
		try {
			aClass = aPool.get(aName);
//			return (Class<T>) aClass.toClass();
			return (Class<T>) Class.forName(aName);
		}
		catch (NotFoundException e) {
			aClass = aPool.makeClass(aInterface.getPackageName()+ ".impl." + aInterface.getSimpleName() + "Impl");
		}
		catch (ClassNotFoundException e) {
			throw new Exception("Previously created class cannot be loaded.", e);
		}

		if (aClass.isFrozen()) {
			aClass.defrost();
		}

		aClass.addInterface(aInterface);

		aClass.addConstructor(CtNewConstructor.defaultConstructor(aClass));

		Map<String, Class> aProps = properties(theInterface);
		for (String aProp : aProps.keySet()) {
			CtField aNewField = new CtField(aPool.get(aProps.get(aProp).getName()), aProp, aClass);

			aClass.addField(aNewField);

			aClass.addMethod(CtNewMethod.getter(getterName(aProp), aNewField));
			aClass.addMethod(CtNewMethod.setter(setterName(aProp), aNewField));
		}

		CtField aIdField = new CtField(aPool.get(SupportsRdfId.class.getName()), "supportsId", aClass);
		aClass.addField(aIdField, CtField.Initializer.byExpr("new com.clarkparsia.empire.annotation.SupportsRdfIdImpl();"));
		aClass.addMethod(CtNewMethod.make("public java.net.URI getRdfId() { return supportsId.getRdfId(); } ", aClass));
		aClass.addMethod(CtNewMethod.make("public void setRdfId(java.net.URI theURI) { return supportsId.setRdfId(theURI); } ", aClass));

		// TODO: generate a more sophisticated equals method based on the fields in the bean
		aClass.addMethod(CtNewMethod.make("public boolean equals(Object theObj) { " +
										  "  if (theObj == this) return true;\n" +
										  "  if (!(theObj instanceof com.clarkparsia.empire.SupportsRdfId)) return false;\n" +
										  "  if (!(this.getClass().isAssignableFrom(theObj.getClass()))) return false;\n" +
										  "  return getRdfId().equals( ((com.clarkparsia.empire.SupportsRdfId) theObj).getRdfId());" +
										  "} ", aClass));

		aClass.addMethod(CtNewMethod.make("public String toString() { return getRdfId() != null ? getRdfId().toString() : super.toString(); } ", aClass));
		aClass.addMethod(CtNewMethod.make("public int hashCode() { return getRdfId() != null ? getRdfId().hashCode() : 0; } ", aClass));

		aClass.freeze();

		return (Class<T>) aClass.toClass();
	}

	/**
	 * Reurn the name of the getter method given the bean property name.  For example, if there is a property "name"
	 * this will return "getName"
	 * @param theProperyName the bean property name
	 * @return the name of the getter for the property
	 */
	private static String getterName(String theProperyName) {
		return "get" + String.valueOf(theProperyName.charAt(0)).toUpperCase() + theProperyName.substring(1);
	}

	/**
	 * Return the name of the setter method given the bean property name.  For example, if there is a property "name"
	 * this will return "setName"
	 * @param theProperyName the bean property name
	 * @return the setter name for the bean property
	 */
	private static String setterName(String theProperyName) {
		return "set" + String.valueOf(theProperyName.charAt(0)).toUpperCase() + theProperyName.substring(1);
	}

	/**
	 * Get the bean proeprties from the given class
	 * @param theClass the bean class
	 * @return a Map of the bean property names with the type the property as the value
	 */
	private static Map<String, Class> properties(Class theClass) {
		Map<String, Class> aMap = new HashMap<String, Class>();

		for (Method aMethod : theClass.getDeclaredMethods()) {
			String aProp = aMethod.getName().substring(3);
			aProp = String.valueOf(aProp.charAt(0)).toLowerCase() + aProp.substring(1);

			Class aType = null;

			if (aMethod.getName().startsWith("get")) {
				aType = aMethod.getReturnType();
			}
			else if (aMethod.getName().startsWith("set") && aMethod.getParameterTypes().length > 0) {
				aType = aMethod.getParameterTypes()[0];
			}

			if (aType != null) {
				aMap.put(aProp, aType);
			}
		}

		return aMap;
	}
}
