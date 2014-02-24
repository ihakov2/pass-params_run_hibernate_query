/**
 * The MIT License (MIT)

Copyright (c) 2007-2010 Ildus Hakov IH Solution Inc. 

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 *
 */

package com.ihsolution.hqipo.dao.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;


/**
 * @author ihakov@gmail.com
 *
 */
public class InputDTO {
	public static final String EQUAL = "equal";
	public static final String NOTEQUAL = "notEqual";
	public static final String LIKE = "like";
	public static final String IN = "in";
	public static final String NOTIN = "notIn";
	public static final String ILIKE = "ilike";// ignore case
	public static final String OR = "or";
	public static final String AND = "and";
	public static final String START_WITH = "right";
	public static final String ANY_MATCH = "both";
	public static final String END_WITH = "left";
	public static final String GREATER_THAN_OR_EQUAL = "ge";
	public static final String LESS_THAN_OR_EQUAL = "le";
	public static final String NOTEXISTS = "notExists";
	public static final String EXISTS = "Exists";
	public static final String NULL="null";
	public static final String NOT_NULL="notNull";
	public static final String GT_PROPERTY = "gtProperty";// the values should be separated by ;(inv.active;inv.adj)
	private List<String> fields = new ArrayList<String>();	//field name or dotted: item.warehouse.warehouseId
	private List values = new ArrayList(); // can be String or Collection
	private List<Integer> types = new ArrayList<Integer>();
	private List<String> operations = new ArrayList<String>(); //equal, like
	private List<String> operators = new ArrayList<String>();	// and or
	private List<String> expressions = new ArrayList<String>(); 
	private Map<String, String> fetchMode = new HashMap<String, String>();
	private Map<String, String> joinTypes = new HashMap<String, String>();
	
	private Map<String, Boolean> sortByMap = new LinkedHashMap<String, Boolean>();
	
	private String	  className;
	private String	  orderBy;
	private boolean doOrder;
	private boolean asc = true;
	private int pageSize;
	private int page=0;
	private String entityName;
	private Class entityClass;
	private long returnedTotalSize;
	private boolean distinct;
	private String[] fieldsToSelect;
	private boolean count;
	
	/**
	 * constructor
	 * @param entityName - the full qualified classname (e.g. <code>com.abc.Customer.class</code>)
	 * @throws ClassNotFoundException
	 * 
	 */
	public InputDTO(String entityName) throws ClassNotFoundException {
		this.entityName = entityName;
		if(entityName==null || "".equals(entityName))
			return;
		this.entityClass = Class.forName(entityName);
	}
	/**
	 * Constructor
	 * @param entityClass - domain class
	 * @throws Exception
	 */
	public InputDTO(Class entityClass) throws Exception {
		this.entityName = entityClass.getCanonicalName();
		this.entityClass = entityClass;
	}
	
	public void putJoinType(String key, String val) {
		this.joinTypes.put(key, val);
	}
	public boolean isSortByMap() {
		return this.sortByMap != null && this.sortByMap.size()>0;
	}

	@Override
	public String toString() {		
		return ""+hashCode();
	}
	/**
	 * Used for EhCache
	 */
	@Override
	public int hashCode() {
		int hc = 17;
		int seed = 31;
		hc = seed * hc + getHash(entityName).iterator().next();
		hc = seed * hc + this.page;
		hc = seed * hc + this.pageSize;
		if(orderBy!=null && !"".equals(orderBy))
			hc = seed * hc + getHash(this.orderBy).iterator().next();
		
		for(int val: getHash(this.fields)) 
			hc = seed * hc + val;
		for(int val: getHash(this.values))
			hc = seed * hc + val;
		for(int val: getHash(this.operations))
			hc = seed * hc + val;		
		
		for(int val: getHash(this.operators))
			hc = seed * hc + val;
		for(int val: getHash(this.expressions))
			hc = seed * hc + val;
		return hc;
	}
	
	private Collection<Integer> getHash(Object obj) {
		Collection<Integer> ret = new ArrayList();
		if(obj == null)
			return null;
		if(obj instanceof String) {
			ret.add(((String)obj).hashCode());
			
		}else if (obj instanceof Number) {
			ret.add(((Number)obj).intValue());				
		}else if(obj instanceof Collection) {
			Collection col = (Collection)obj;			
			for(Object o: col) {
				if(o == null)
					continue;
				for(int val: getHash(o)) {
					ret.add(val);
				}
			}				
		}else
			ret.add(obj.hashCode());
		return ret;
	}

	public void addSqlExpression(String expr) {
		if(expr==null || "".equals(expr))
			return;
		this.expressions.add(expr);
	}
	/**
	 * Add parameters to Where clause
	 * @param field
	 * @param value
	 * @param operations (see constants)
	 * @param andOr  (see constants)
	 * @return
	 */
	public InputDTO addWCFieldAndValue(String field, Object value, String operation, String andOr) {
		fields.add(field);
		values.add(value);
		types.add(new Integer(0));
		operations.add(operation);
		boolean and = (andOr==null || AND.equals(andOr));
		operators.add(and? AND: OR);	
		return this;
	}
	/**
	 * Method to insert where clause params
	 * @param field name
	 * @param value
	 * @param type = 0
	 * @param operation (see constants of this class)
	 * @param operator - AND OR (if pass null it's an AND)
	 * @param index - to where in the list insert 
	 * @return
	 */
	public InputDTO insertWCFieldAndValue(String field, Object value, int type, String operation, String operator, int index) {
		fields.add(index, field);
		values.add(index, value);
		types.add(index, new Integer(type));
		operations.add(index,operation);
		operators.add(index, operator);
		return this;
	}

	public int removeFromCollections(String fieldName) {
		int ind = -1;
		if(fields.contains(fieldName)) {
			ind = fields.indexOf(fieldName);
			this.fields.remove(ind);
			this.operations.remove(ind);
			this.operators.remove(ind);
			this.values.remove(ind);
		}
		return ind;
	}
	public void clearTheFieldsAndValues() {
		this.fields = new ArrayList<String>();
		this.values = new ArrayList<String>();
		this.operations = new ArrayList<String>();
		this.operators = new ArrayList<String>();
		this.expressions = new ArrayList<String>();
	}
	public int getIndex(String fieldName) {
		return fields.indexOf(fieldName);
	}
	public String getFieldValue(int index) {
		return fields.get(index);
	}
	public Object getValueValue(int index) {
		return values.get(index);
	}
	public String getOperValue(int index) {
		return operations.get(index);
	}
	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public List<String> getFields() {
		return fields;
	}

	public void setFields(List<String> fields) {
		this.fields = fields;
	}

	public List<String> getOperations() {
		return operations;
	}

	public void setOperations(List<String> operations) {
		this.operations = operations;
	}

	public List<String> getOperators() {
		return operators;
	}

	public void setOperators(List<String> operators) {
		this.operators = operators;
	}

	public String getOrderBy() {
		return orderBy;
	}

	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}

	public List<Integer> getTypes() {
		return types;
	}

	public void setTypes(ArrayList<Integer> types) {
		this.types = types;
	}

	public List<String> getValues() {
		return values;
	}

	public void setValues(ArrayList<String> values) {
		this.values = values;
	}

	public boolean isAsc() {
		return asc;
	}

	public void setAsc(boolean asc) {
		this.asc = asc;
	}

	public boolean isDoOrder() {
		return doOrder;
	}

	public void setDoOrder(boolean doOrder) {
		this.doOrder = doOrder;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}
	public InputDTO nextPage() {
		this.page++;
		return this;
	}
	public InputDTO prevPage() {
		this.page = (this.page == 0? this.page: --(this.page));
		return this;
	}
	public InputDTO firstPage() {
		this.page = 0;
		return this;
	}
	public InputDTO specificPage(int page) {
		this.page = page;
		return this;
	}

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public long getReturnedTotalSize() {
		return returnedTotalSize;
	}

	public void setReturnedTotalSize(long returnedTotalSize) {
		this.returnedTotalSize = returnedTotalSize;
	}
	public List<String> getExpressions() {
		return expressions;
	}
	public void setExpressions(List<String> expressions) {
		this.expressions = expressions;
	}
	public Map<String, String> getFetchMode() {
		return fetchMode;
	}
	public void setFetchMode(Map<String, String> fetchMode) {
		this.fetchMode = fetchMode;
	}
	public boolean isDistinct() {
		return distinct;
	}
	public void setDistinct(boolean distinct) {
		this.distinct = distinct;
	}
	public Class getEntityClass() {
		return entityClass;
	}
	public void setEntityClass(Class entityClass) {
		this.entityClass = entityClass;
	}
	public String[] getFieldsToSelect() {
		return fieldsToSelect;
	}
	public void setFieldsToSelect(String[] fieldsToSelect) {
		this.fieldsToSelect = fieldsToSelect;
	}
	public boolean isCount() {
		return count;
	}
	public void setCount(boolean count) {
		this.count = count;
	}
	public Map<String, String> getJoinTypes() {
		return joinTypes;
	}
	public void setJoinTypes(Map<String, String> joinTypes) {
		this.joinTypes = joinTypes;
	}
	public Map<String, Boolean> getSortByMap() {
		if(this.sortByMap == null)
			this.sortByMap = new LinkedHashMap<String, Boolean>();
		return sortByMap;
	}
	public void setSortByMap(Map<String, Boolean> sortByMap) {
		this.sortByMap = sortByMap;
	}
}
