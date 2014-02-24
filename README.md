pass-params_run_hibernate_query
===============================

Pass parameters from front end (forms, request parameters) to service layer to execute Hibernate query.

 
Part1. Hibernate query input parameters object (InputDTO.class)

InputDTO allows to pass parameters from front end beans, controllers to service layer to execute the Hibernate queries
in simple manner even not knowing how hibernate Criteria, DetachedCritiria, Projection, Restriction classes work.
InputDTO is POJO class with no dependency to Hibernate classes. So it can be used anywhere.

Limitation: Not all possible cases implemented offered by Hibernate Criteria.class. 
			See InputDto final statics what is implemented.

Lets assume the following db:

Client      Proj    Proj_empl   Empl    Role
-------	    ----    ---------   ----    -----
       ---<     ---<         >--    >---
-------	    ----    ---------   ----    ------
 // domain db object (shown only part of Empl)
 public class Empl {
	 @Column(name="first_name")
    private String firstName;
	...
	@JoinColumn(name = "roleId")
    @ManyToOne(optional = false)
    private Role role ;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "emplId")
    private Collection<ProjEmpl> projEmplCollection;
	...
 }
 Run query
 if form provided, get employees working on not on project1 and with roles PM and Developer, if fullTime is true,
 query only full time empls. and if client FK is not null and first name starts with form.getFirstName()  order by project name descending.
 if form is not provided, return all employees sorted by last name.
 
 Frontend bean or MVC controller
 ...
 @Autowired
	private Service1 service1;
@Autowired
	private Service2 service2;
 
 public List<Empl> getEmployees(EmplForm form) {
	...
	// Contructor if hibernate classes and domain objects are not exposed to front end
	InputDTO in = new InputDTO("com.test.model.Empl"); 
	// Constructor if hibernate and domain objects are exposed to frontend
	InputDTO in = new InputDTO(Empl.class);
	
	if(form==null) { // get all empls
		in.setOrderBy("lastName");
		in.doOrder(true);
		return this.service1.getObjects(in);
	}
	// Get empls by where clause
	// optional selected fields, if set then hibernate returns List<Object[]> Object[0] is id, Object[1] is firstName... for this example
	//String[] fieldsToReturn = new String[] {"id", "firstName", "lastName"};
	//in.setFieldsToSelect(fieldsToReturn);
	
	// We are not using fieldsToSelect so Hibernate will return List<Empl>
	
	// NOT EQUAL
	in.addWcFieldAndValue("projEmplCollection.proj.name", form.getProject(), InputDTO.NOT_EQUAL, InputDTO.AND);
	String[] roles=new String[]{"PM","Developer"}; // can be collection too
	// IN
	in.addWcFieldAndValue("role.name", roles, InputDTO.IN, InputDTO.AND);
	if(form.isEmpFullTime()) {
		// EQUAL
		in.addWcFieldAndValue("fullTime", "Y", InputDTO.EQUAL, InputDTO.AND);
	// NOT NULL
	in.addWcFieldAndValue("projEmplCollection.proj.client", null, InputDTO.NOT_NULL, InputDTO.AND);

	// LIKE operation
	in.addWcFieldAndValue("firstName", form.firstName, InputDTO.START_WITH, InputDTO.AND); // analog ilike 'john%' case insencitive
	// optional
	in.setOrderBy("projEmplCollection.proj.name");
	in.setAsc(false);
	in.doOrder(true);
	in.setPageSize(10);
	in.setPage(1);

	//if frontend not aware about hibernate or domain objects, service returns converted domain objects to dtos
	List<EmplDtos> empls = this.service1.getEmployess(in);

	// if frontend aware about hibernate and domain objects
	List<Empl> empls = this.service1.getObjects(in); // see below implementation
	// get the same results from service2
	empls = this.service2.getObjects(in);
	return empls;
}

Part 2. Hibernate query helper (QueryHelper.class).

QueryHelper converts InputDTO to Hibernate Criteria object to be able to run Hibernate query.

Setting the services like shown below gives the flexibility to extend any service to access any table (domain object).
// interfaces are not shown
public class Service1Impl extends BaseServiceImpl implements Service1 {

	// return empl dtos only
	@Override
	public List<EmplDto> getEmployess(InputDTO in) {
		List<Empl> empls = super.getObjects(in);
		return convertToDto(empls);
	}
}
public class Service2Impl extends BaseServiceImpl implements Service2 {
	
}
 
public class BaseServiceImpl implements BaseService {
	@Autowired
	private AnyObjectDao anyObjectDao;
	
	// return any domain objects
	@Override
	public List getObjects(InputDTO in) {
		return this.anyObjectDao.getObjects(in);
	}
	...
}	
// This implementaion uses JPA
@Override
	@Transactional
public class AnyObjectDaoImpl extends AbstractJpaDao implements AnyObjectDao {
	...
	@PersistenceContext(unitName = "TestDb")
	private EntityManager entityManager;
	...
	public List getObjects(InputDTO dto) throws Exception {
		QueryHelper qh = new QueryHelper(dto.getEntityName());
		Session session = this.entityManager.unwrap(Session.class);
		dto.setCount(false);
		qh.convertDtoToQhelper(dto);
		Criteria cr = qh.getDetCriteria().getExecutableCriteria(session);
		qh.applyOrderBy();
		if(qh.getPage() >0)
			cr = cr.setFirstResult(qh.getPage()*qh.getPageSize());
		List res = cr.list();
		if(logger.isDebugEnabled()) logger.debug("found: "+res.size());
		return res;
	}
}
// for hibernate the implementation the same except a session.
//	use hibernate session from SessionFactory
	Session session = sessionFactory.getCurrentSession();
	
