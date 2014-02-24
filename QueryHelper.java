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
 */


package com.ihsolution.hqipo.dao.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.Hibernate;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Example;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.Junction;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.Type;

/**
 * @author ihakov@gmail.com
 *
 */
public class QueryHelper {
	
	private String orderBy;
	private boolean doOrder;
	private boolean asc = true;
	private int pageSize=1000;
	private int page=0;
	private Class targetClass;

	private Example example;
    private DetachedCriteria detCriteria;
    static Logger logger=Logger.getLogger(QueryHelper.class);
    private ArrayList<String> aliases = new ArrayList<String>();
    private ArrayList<String> projections = new ArrayList<String>();
    private int lockMode;
    private boolean fldSelectedSet;
    private InputDTO dto;
    private Projection countProjection;

    public QueryHelper(Class targetClass) {
    	
    	this.targetClass = targetClass;
		detCriteria = DetachedCriteria.forClass(targetClass);	
	}
    
	public QueryHelper(String entName) throws Exception {
		Class cls=Class.forName(entName);
		this.targetClass = cls;
		detCriteria = DetachedCriteria.forClass(cls);
	}
	static public MatchMode getMatchMode(InputDTO dto, int index) {
		String val = dto.getOperValue(index);
		return getMatchMode(val);
	}	
	static public MatchMode getMatchMode(String val) {
		if(val != null && !"".equals(val)) {
			if(val.equals("both"))
				return MatchMode.ANYWHERE;
			if(val.equals("left"))
				return MatchMode.END;
			if(val.equals("right"))
				return MatchMode.START;
			if(val.equals("equal"))
				return MatchMode.EXACT;
			if(val.equals("like") || val.equals("ilike"))
				return MatchMode.START;
		}
		//default
		return null;
	}
	// will create query helper for count and add orderby
	public QueryHelper convertDtoToQhelper(InputDTO dto) {
			this.dto = dto;
			this.convertDtoToQhelperForCount(dto);
			asc = dto.isAsc()? true: false;
			this.doOrder = dto.isDoOrder()? true: false;
			this.orderBy = dto.getOrderBy();
			this.page = dto.getPage();
			this.pageSize = dto.getPageSize();
			
		return this;	
	}
	/**
	 * convert the InputDTO to QueryHelper to get the count (omitting sort order)
	 */
	@SuppressWarnings("deprecation")
	public QueryHelper convertDtoToQhelperForCount (InputDTO dto) {
		if(dto == null)
			return this;
		this.dto = dto;
		int ind = 0;
		try {
			Junction j = null;
			Junction conj = Restrictions.conjunction();
			Junction disj = Restrictions.disjunction();
			String operator = "";
			boolean disjB = false, conjB = false;
			if(dto.getFetchMode()!=null) {
				for(Map.Entry<String, String> entry: dto.getFetchMode().entrySet()) {
					FetchMode fmode = null;
					logger.debug("FetchMode key="+entry.getKey()+" val="+entry.getValue());
					if(entry.getValue().equals("join"))
						fmode = FetchMode.JOIN;
					else if(entry.getValue().equals("eager"))
						fmode = FetchMode.EAGER;
					else if(entry.getValue().equals("lazy"))
						fmode = FetchMode.LAZY;
					else
						fmode = FetchMode.LAZY;
					this.detCriteria.setFetchMode(entry.getKey(), fmode);
				}
			}
			for(String field: dto.getFields()) {		
				operator = dto.getOperators().get(ind);
				if("or".equals(operator)) {
					j = disj;
					disjB = true;
				}else {
					j= conj;
					conjB = true;
				}
				this.addFieldAndVal(createAliases(field), dto.getValues().get(ind), 
						dto.getOperations().get(ind), j);
				ind++;			
			}
			
			if(dto.getExpressions() != null) {
				for(String expr: dto.getExpressions()) {
					j.add(Expression.sql(expr));
				}
			}
			if(dto.getFieldsToSelect()!=null && dto.getFieldsToSelect().length > 0) {
				ProjectionList prList = Projections.projectionList();
				Projection projection = null;
				
				for(String fld: dto.getFieldsToSelect()) {
					String als = this.createAliases(fld);
						prList.add(Projections.property(als));					
				}
					if(dto.isDistinct()) {
						projection = Projections.distinct(prList);
					}else {	
						projection = prList;
					}
					this.detCriteria.setProjection(projection);
				
			}else {
				this.fldSelectedSet = false;
			}
			
			
			if(disjB) 
				detCriteria.add(disj);
			if(conjB)
				detCriteria.add(conj);				
			if(logger.isDebugEnabled()) {
				if(conjB)
					logger.debug("conjuction="+conj.toString());
				if(disjB)
					logger.debug("disjunction="+disj.toString());
			}
			if(dto.isDistinct())
				detCriteria.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		} catch(Exception e) {
			e.printStackTrace();
			logger.error(e);
		}		
		return this;		
	}
	protected String createAliases(String field) {
		String [] fStrs = field.split("\\.");
		String tmpField="", fetchModeValue = "";
		int len = fStrs.length;
		
		if(len > 1) {
			for(int i=0; len - i != 1; i++) {						
				if(i == 0) {								
					if(!aliases.contains(fStrs[i])) {
						if(this.dto.getJoinTypes().containsKey(fStrs[i])) {
							
							fetchModeValue = dto.getJoinTypes().get(fStrs[i]);
							logger.info(fStrs[i]+"="+fetchModeValue);
							//FetchMode fmode = null;
							int joinType = 0; 
							if(fetchModeValue.equals("left_join"))
								joinType = CriteriaSpecification.LEFT_JOIN;//FetchMode.JOIN;
							else if(fetchModeValue.equals("inner_join"))
								joinType = CriteriaSpecification.INNER_JOIN;//.FetchMode.EAGER;
							else
								joinType = CriteriaSpecification.INNER_JOIN;//.FetchMode.EAGER;
							
							detCriteria.createAlias(fStrs[i], fStrs[i], joinType );
						}else {
							detCriteria.createAlias(fStrs[i], fStrs[i]);
						}
						aliases.add(fStrs[i]);
					}
				}else {
					if(!aliases.contains(fStrs[i])){
						String str = fStrs[i-1] +"."+ fStrs[i];
						detCriteria.createAlias(str, fStrs[i]);
						aliases.add(fStrs[i]);
					}
				}				
			}
			tmpField = fStrs[len-2]+"."+fStrs[len-1];
		}else
			tmpField = field;
		
		return tmpField;
	}
	
	protected String createProjection(String field)  {
		String [] fStrs = field.split("\\.");
		String tmpField="";
		int len = fStrs.length;
		ProjectionList prList = Projections.projectionList();
		
		if(len > 1) {
			for(int i=0; len - i != 1; i++) {						
				if(i == 0) {								
					if(!projections.contains(fStrs[i])) {
						prList.add(Projections.property(fStrs[i]));
						projections.add(fStrs[i]);
					}
				}else {
					if(!projections.contains(fStrs[i])){
						String str = fStrs[i-1] +"."+ fStrs[i];
						prList.add(Projections.property(str), fStrs[i]);
						projections.add(fStrs[i]);
					}
				}
			}
			detCriteria.setProjection(prList);
			tmpField = fStrs[len-1];
		}else
			tmpField = field;
		
		return tmpField;
	}
	/**
	 * Main conversion method
	 * @param fieldName
	 * @param fieldVal
	 * @param oper (=, equal, IN ...)
	 * @param j (AND OR)
	 * @return
	 */
	public QueryHelper addFieldAndVal(String fieldName, Object fieldVal, String oper, Junction j) {
		
		boolean isValString = fieldVal instanceof String;
		String str = "";
		if(oper==null || "".equals(oper)) {			
			oper = "equal";
		}
		
		if(isValString)
			str = ((String)fieldVal).trim();
		if("equal".equals(oper)) {
			if(isValString) {				
				j.add(Restrictions.eq(fieldName, str).ignoreCase());
			}else
				j.add(Restrictions.eq(fieldName,fieldVal));
		}else if("notEqual".equals(oper)) {
			if(isValString) {				
				j.add(Restrictions.ne(fieldName, str).ignoreCase());
			}else
				j.add(Restrictions.ne(fieldName,fieldVal));
		}else if("null".equals(oper)) {
			j.add(Restrictions.isNull(fieldName));
		}else if("notNull".equals(oper)) {
			j.add(Restrictions.isNotNull(fieldName));
		}else if("notExists".equals(oper)) {
			j.add(Restrictions.sqlRestriction(fieldVal.toString()));
		}else if("Exists".equals(oper)) {
			j.add(Restrictions.sqlRestriction(fieldVal.toString()));
		}else if(isValString) {
			MatchMode mm = getMatchMode(oper);
			if(mm != null)
				j.add(Restrictions.ilike(fieldName, str, mm));
		}else if("le".equals(oper))
			j.add(Restrictions.le(fieldName,fieldVal));
	
		else if("ge".equals(oper))
			j.add(Restrictions.ge(fieldName, fieldVal));
		else if("gtProperty".equals(oper)) {
			String [] spl = ((String)fieldVal).split(";");
			if(spl.length ==2)
				j.add(Restrictions.gtProperty(spl[0], spl[1]));
			else
				j.add(Restrictions.gt(fieldName, fieldVal));
		}else if("in".equals(oper)) {
			if(fieldVal instanceof Collection)		
				j.add(Restrictions.in(fieldName, (Collection)fieldVal));
			else if(fieldVal instanceof Object[])
				j.add(Restrictions.in(fieldName, (Object[])fieldVal));
			else
				throw new IllegalArgumentException("QueryHelper.IN illegal argument type. Should be Collection or Object[]");
		}else if("notIn".equals(oper)) {
			if(fieldVal instanceof Collection)		
				j.add(Restrictions.not(Restrictions.in(fieldName, (Collection)fieldVal)));
			else if(fieldVal instanceof Object[])
				j.add(Restrictions.not(Restrictions.in(fieldName, (Object[])fieldVal)));
			else
				throw new IllegalArgumentException("QueryHelper.NOTIN illegal argument type. Should be Collection or Object[]");		
			
		}else if("between".equals(oper)) {
			Collection objs = (Collection)fieldVal;
			Iterator it2 = objs.iterator();
			Object obj1 = it2.next();
			Object obj2 = it2.next();
	
			j.add(Restrictions.between(fieldName,obj1 instanceof String? obj1.toString().toLowerCase():obj1
					,obj2 instanceof String? obj2.toString().toLowerCase(): obj2));		
		}else
			j.add(Restrictions.eq(fieldName, fieldVal));
		
		return this;
	}

	public String createSqlAliasAndField(String aliasAndOrField) {
		String alias = aliasAndOrField;
		String aArr[] = alias.split("\\.");
		String sqlAlias = "", sqlField = "";
		int len = aArr.length; 
		if(len > 1) {
			this.createAliases(alias.trim());
			sqlAlias = this.getSqlAlias(aArr[len-2]);
			sqlField = this.convertJava2Db(aArr[len-1]);
		}else {
			sqlAlias = "this_";
			sqlField = this.convertJava2Db(alias);
		}
		return sqlAlias.concat(".").concat(sqlField);
	}
	protected String getSqlAlias(String alias) {
		String a = alias;
		int ind = aliases.indexOf(a)+1;
		if(ind == -1) {
			logger.error("Element "+a+" not found in aliases");
			return a.concat("_");
		}
		String sqlAlias = a.substring(0, a.length()-1)+ ind+ "_";
		return sqlAlias;
	}
	protected String convertJava2Db(String field) {
		String f = field;
		int ind = 0;
		for(Character c: f.toCharArray()) {
			if(Character.isJavaLetterOrDigit(c) && Character.isUpperCase(c)) {
//				c = Character.toLowerCase(c);
				break;
			}
			ind++;
		}
		String dbField = f.substring(0, ind).concat("_").concat(f.substring(ind));
		return dbField.toLowerCase();
	}
	
	public void applyOrderBy() throws Exception {
		if(!doOrder) return;
		if(dto == null)
			throw new Exception("dto is null");
		Map<String, Boolean> sortBy = dto.getSortByMap();
//		logger.debug("order by Map size="+sortBy.size());
		if((this.orderBy==null || "".equals(orderBy)) && !dto.isSortByMap()) {			
			return;
		}
		// Map has highest priority
		if(dto.isSortByMap()) {
			for(Map.Entry<String, Boolean> entry: sortBy.entrySet()) {
				String alias = this.createAliases(entry.getKey());
				if(logger.isDebugEnabled())
					logger.debug("Using sortByMap order by = "+alias);
				if(entry.getValue())		
					detCriteria.addOrder(Order.asc(alias).ignoreCase());
				else
					detCriteria.addOrder(Order.desc(alias).ignoreCase());
			}
			return;
		}
		// if we have arithmetic operators, checking only for plus for now
		if(orderBy.contains("+")) {
			
			String [] fields = orderBy.split("\\+");
			if(fields.length > 1) {
				String sql = this.createSqlAliasAndField(fields[0])+"+"+this.createSqlAliasAndField(fields[1])+ " as sF";
				detCriteria.setProjection(Projections.projectionList()						
					.add(Projections.alias(Projections.sqlProjection(sql, new String[]{"sF"}, new Type[] {Hibernate.INTEGER}),"sumF"))
					.add(Projections.sqlProjection("this_.*", new String[] {this.targetClass.getSimpleName()+"_id"}, new Type[] {Hibernate.entity(this.targetClass)})));
				detCriteria.addOrder(Order.desc("sumF"));
				if(fields.length > 2) {
					String alias = this.createAliases(fields[2]);
					detCriteria.addOrder(Order.asc(alias).ignoreCase());
				}
				return;
			}
		}
		if(orderBy != null && !"".equals(orderBy)) {
			String [] obs = orderBy.split(";");
			for(String ob: obs) {
				String alias = this.createAliases(ob);
				if(logger.isDebugEnabled())
					logger.debug("order by = "+alias);
				if(asc)		
					detCriteria.addOrder(Order.asc(alias).ignoreCase());
				else
					detCriteria.addOrder(Order.desc(alias).ignoreCase());
			}
		}
	}

	public void applyOrderBy(Criteria criteria) {
		if(orderBy != null && !"".equals(orderBy)) {
			if(asc)		
				criteria.addOrder(Order.asc(orderBy));
			else
				criteria.addOrder(Order.desc(orderBy));
		}
	}
	
	public QueryHelper nextPage() {
		this.page++;
		return this;
	}

	public QueryHelper prevPage() {
		this.page = (this.page == 0? this.page: --(this.page));
		return this;
	}
	
	public QueryHelper firstPage() {
		this.page = 0;
		return this;
	}
	
	public QueryHelper specificPage(int page) {
		this.page = page;
		return this;
	}

	/**
	 * @return the orderBy
	 */
	public String getOrderBy() {
		return orderBy;
	}

	/**
	 * @param orderBy the orderBy to set
	 */
	public void setOrderBy(String orderBy) {
		this.orderBy = orderBy;
	}

	/**
	 * @return the asc
	 */
	public boolean isAsc() {
		return asc;
	}

	/**
	 * @param asc the asc to set
	 */
	public void setAsc(boolean asc) {
		this.asc = asc;
	}

	/**
	 * @return the pageSize
	 */
	public int getPageSize() {
		return pageSize;
	}

	/**
	 * @param pageSize the pageSize to set
	 */
	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	/**
	 * @return the targetClass
	 */
	public Class getTargetClass() {
		return targetClass;
	}

	/**
	 * @return the page
	 */
	public int getPage() {
		return page;
	}

	/**
	 * @param page the page to set
	 */
	public void setPage(int page) {
		this.page = page;
	}

	/**
	 * @return the example
	 */
	public Example getExample() {
		return example;
	}

	/**
	 * @param example the example to set
	 */
	public void setExample(Example example) {
		this.example = example;
	}

	/**
	 * @return the detCriteria
	 */
	public DetachedCriteria getDetCriteria() {
		return detCriteria;
	}

	/**
	 * @param detCriteria the detCriteria to set
	 */
	public void setDetCriteria(DetachedCriteria detCriteria) {
		this.detCriteria = detCriteria;
	}

	/**
	 * @return the doOrder
	 */
	public boolean isDoOrder() {
		return doOrder;
	}

	/**
	 * @param doOrder the doOrder to set
	 */
	public void setDoOrder(boolean doOrder) {
		this.doOrder = doOrder;
	}
	
	/**
	 * @return the lockMode
	 */
	public int getLockMode() {
		return lockMode;
	}
	
	/**
	 * @param lockMode the lockMode to set
	 */
	public void setLockMode(int lockMode) {
		this.lockMode = lockMode;
	}

	public boolean isFldSelectedSet() {
		return fldSelectedSet;
	}

	public void setFldSelectedSet(boolean fldSelectedSet) {
		this.fldSelectedSet = fldSelectedSet;
	}

	public Projection getCountProjection() {
		return countProjection;
	}

	public void setCountProjection(Projection countProjection) {
		this.countProjection = countProjection;
	}

}

