/**
 * 
 */
package uk.ac.ucl.excites.collector.database;

import java.util.List;

import uk.ac.ucl.excites.collector.project.model.Form;
import uk.ac.ucl.excites.collector.project.model.Project;
import uk.ac.ucl.excites.collector.project.util.DuplicateException;
import uk.ac.ucl.excites.storage.model.Record;
import uk.ac.ucl.excites.storage.model.Schema;
import uk.ac.ucl.excites.transmission.Transmission;
import uk.ac.ucl.excites.transmission.sms.SMSTransmission;
import uk.ac.ucl.excites.transmission.sms.SMSTransmissionID;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Predicate;


/**
 * @author mstevens, julia, Michalis Vitos
 * 
 */
public final class DataAccess
{

	// Statics----------------------------------------------
	static protected final String TAG = "DATA ACCESS";
	static public final int ACTIVATION_DEPTH = 40;
	static public final int UPDATE_DEPTH = 40;

	// Dynamics---------------------------------------------
	private ObjectContainer db;
	
	public DataAccess(ObjectContainer db)
	{
		this.db = db;
	}
	
	public void commit()
	{
		db.commit();
	}

	/**
	 * @param schema
	 */
	public void store(Schema schema)
	{
		storeObj(schema);
	}

	/**
	 * Retrieves all schemata
	 * 
	 * @return
	 */
	public List<Schema> retrieveSchemata()
	{
		List<Schema> result = db.query(Schema.class);
		for(Schema s : result)
			db.activate(s, ACTIVATION_DEPTH);
		return result;
	}

	/**
	 * @param record - the record to store
	 */
	public void store(Record record)
	{
		storeObj(record);
	}
	
	/**
	 * @param record - the record to delete
	 */
	public void delete(Record record)
	{
		deleteObj(record);
	}
	
	/**
	 * Retrieve all Records
	 * 
	 * @return
	 */
	public List<Record> retrieveRecords()
	{
		List<Record> result = db.query(Record.class);
		for(Record r : result)
			db.activate(r, ACTIVATION_DEPTH);
		return result;
	}
	
	/**
	 * Retrieve Records by their Schema
	 * 
	 * @param schema
	 * @return
	 */
	public List<Record> retrieveRecords(final Schema schema)
	{
		ObjectSet<Record> result = db.query(new Predicate<Record>()
		{
			private static final long serialVersionUID = 1L;
			
			public boolean match(Record record)
			{
				return record.getSchema() == schema;
			}
		});
		for(Record r : result)
			db.activate(r, ACTIVATION_DEPTH);
		return result;
	}
	
	public List<Record> retrieveRecordsWithoutTransmission(Schema schema)
	{
		return retrieveRecords(schema, null); //will return records without a transmission
	}
	
	/**
	 * Retrieve Records of a given Schema which are associated with a given transmission
	 * 
	 * @param schema
	 * @return
	 */
	public List<Record> retrieveRecords(final Schema schema, final Transmission transmission)
	{
		ObjectSet<Record> result = db.query(new Predicate<Record>()
		{
			private static final long serialVersionUID = 1L;
			
			public boolean match(Record record)
			{
				return record.getSchema() == schema && record.getTransmission() == transmission;
			}
		});
		for(Record r : result)
			db.activate(r, ACTIVATION_DEPTH);
		return result;
	}
	
	/**
	 * @param id
	 * @param version
	 * @return
	 */
	public Schema retrieveSchema(final int id, final int version)
	{
		ObjectSet<Schema> result = db.query(new Predicate<Schema>()
		{
			private static final long serialVersionUID = 1L;
			
			public boolean match(Schema schema)
			{
				return schema.getID() == id && schema.getVersion() == version;
			}
		});
		if(result.hasNext())
		{
			Schema s = result.next();
			db.activate(s, ACTIVATION_DEPTH);
			return s;
		}
		else
			return null;
	}
	
	/**
	 * @param project
	 */
	public void store(Project project) throws DuplicateException
	{
		if(retrieveProject(project.getName(), project.getVersion()) != null)
			throw new DuplicateException("There is already a project named \"" + project.getName() + "\", with version " + project.getVersion() + ". Either remove the existing one or increment the version of the new one.");
		storeObj(project);
	}
	
	/**
	 * @param project
	 */
	public void update(Project project)
	{
		storeObj(project);
	}

	/**
	 * Retrieves all projects
	 * 
	 * @return
	 */
	public List<Project> retrieveProjects()
	{
		final List<Project> result = db.queryByExample(Project.class);
		for(Project p : result)
			db.activate(p, ACTIVATION_DEPTH);
		return result;
	}

	/**
	 * Retrieves specific Project
	 * 
	 * @return null if project was not found
	 */
	public Project retrieveProject(final String name, final String version)
	{
		@SuppressWarnings("serial")
		ObjectSet<Project> result = db.query(new Predicate<Project>()
		{
			public boolean match(Project project)
			{
				return project.getName().equalsIgnoreCase(name) && project.getVersion().equalsIgnoreCase(version);
			}
		});
		if(result.isEmpty())
			return null;
		else
		{
			Project p = result.get(0);
			db.activate(p, ACTIVATION_DEPTH);
			return p;
		}
	}

	/**
	 * Delete specific project
	 * 
	 * @return
	 */
	public void delete(Project project)
	{
		deleteObj(project);
	}
	
	/**
	 * Retrieve a form by its schemaID & version
	 * 
	 * @param schemaID
	 * @param schemaVersion
	 * @return
	 */
	public Form retrieveForm(final int schemaID, final int schemaVersion)
	{
		ObjectSet<Form> result = db.query(new Predicate<Form>()
		{
			private static final long serialVersionUID = 1L;
			
			public boolean match(Form form)
			{
				return form.getSchemaID() == schemaID && form.getSchemaVersion() == schemaVersion;
			}
		});
		if(result.hasNext())
		{
			Form f = result.next();
			db.activate(f, ACTIVATION_DEPTH);
			return f;
		}
		else
			return null;
	}
	
	/**
	 * @param transmission
	 */
	public void store(Transmission transmission)
	{
		storeObj(transmission);
	}
	
	/**
	 * Retrieves all transmissions
	 * 
	 * @return
	 */
	public List<Transmission> retrieveTransmissions()
	{
		final List<Transmission> result = db.queryByExample(Transmission.class);
		for(Transmission t : result)
			db.activate(t, ACTIVATION_DEPTH);
		return result;
	}

	/**
	 * Retrieves all unsent transmissions
	 * 
	 * @return
	 */
	public List<Transmission> retrieveUnsentTransmissions()
	{
		@SuppressWarnings("serial")
		ObjectSet<Transmission> result = db.query(new Predicate<Transmission>()
		{
			public boolean match(Transmission t)
			{
				return !t.isSent();
			}
		});
		for(Transmission t : result)
			db.activate(t, ACTIVATION_DEPTH);
		return result;
	}
	
	/**
	 * Retrieve SMSTransmission by id
	 * 
	 * @param id
	 * @return
	 */
	public SMSTransmission retrieveSMSTransmission(final int id)
	{
		@SuppressWarnings("serial")
		ObjectSet<SMSTransmission> result = db.query(new Predicate<SMSTransmission>()
		{
			public boolean match(SMSTransmission t)
			{
				return t.getID() == id;
			}
		});
		if(result.isEmpty())
			return null;
		else
		{
			SMSTransmission t = result.get(0);
			db.activate(t, ACTIVATION_DEPTH);
			return t;
		}		
	}
	
	/**
	 * @param id
	 */
	public void store(SMSTransmissionID id)
	{
		storeObj(id);
	}
	
	public SMSTransmissionID retrieveTransmissionID()
	{
		List<SMSTransmissionID> result = db.query(SMSTransmissionID.class);
		if(result.isEmpty())
			return null;
		return result.get(0);
	}

	public void storeObj(Object obj)
	{
		db.store(obj);
		db.commit();
	}

	public void deleteObj(Object obj)
	{
		db.delete(obj);
		db.commit();
	}
}