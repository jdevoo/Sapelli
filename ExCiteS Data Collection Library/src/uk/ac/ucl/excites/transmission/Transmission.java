/**
 * 
 */
package uk.ac.ucl.excites.transmission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import uk.ac.ucl.excites.storage.model.Column;
import uk.ac.ucl.excites.storage.model.Record;
import uk.ac.ucl.excites.storage.model.Schema;

/**
 * @author mstevens
 * 
 */
public abstract class Transmission
{

	protected DateTime sentAt = null; //used only on sending side
	protected DateTime receivedAt = null; //used on receiving side, and TODO on sending side once we have acknowledgements working
	
	protected SchemaProvider schemaProvider; //only used on the receiving side
	protected Schema schema;
	protected Set<Column<?>> columnsToFactorOut;
	protected Map<Column<?>, Object> factoredOutValues = null;
	protected List<Record> records;

	public Transmission(Schema schema)
	{
		this(schema, null);
	}
	
	/**
	 * To be called at the sending side.
	 * 
	 * @param schema
	 * @param columnsToFactorOut
	 */
	public Transmission(Schema schema, Set<Column<?>> columnsToFactorOut)
	{
		this(); //!!!
		if(schema == null)
			throw new NullPointerException("Schema cannot be null on sending side.");
		this.schema = schema;
		setColumnsToFactorOut(columnsToFactorOut);
	}
	
	/**
	 * To be called at the receiving side.
	 * 
	 * @param schemaProvider
	 */
	public Transmission(SchemaProvider schemaProvider)
	{
		this(); //!!!
		if(schemaProvider == null)
			throw new NullPointerException("SchemaProvider cannot be null on receiving side.");
		this.schemaProvider = schemaProvider;
	}
	
	private Transmission()
	{
		this.factoredOutValues = new HashMap<Column<?>, Object>();
		this.records = new ArrayList<Record>();
	}
	
	protected void setColumnsToFactorOut(Set<Column<?>> columnsToFactorOut)
	{
		if(columnsToFactorOut == null)
			columnsToFactorOut = new HashSet<Column<?>>();
		else
		{
			for(Column<?> c : columnsToFactorOut)
				if(schema.getColumnIndex(c) == Schema.UNKNOWN_COLUMN_INDEX)
					throw new IllegalArgumentException(c.toString() + " does not belong to the given schema.");
		}
		this.columnsToFactorOut = columnsToFactorOut;
	}
	
	public abstract boolean addRecord(Record record) throws Exception;
	
	public List<Record> getRecords()
	{
		return records;
	}
	
	public abstract void send() throws Exception;
	
	public boolean isEmpty()
	{
		return records.size() == 0;
	}
	
	public boolean isSent()
	{
		return sentAt != null;
	}

	public boolean isReceived()
	{
		return receivedAt != null;
	}
	
}