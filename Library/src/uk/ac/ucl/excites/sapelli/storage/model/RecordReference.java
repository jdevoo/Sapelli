/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.storage.model;

import java.io.IOException;

import uk.ac.ucl.excites.sapelli.storage.model.indexes.PrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.AndConstraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.EqualityConstraint;
import uk.ac.ucl.excites.sapelli.storage.util.IncompletePrimaryKeyException;
import uk.ac.ucl.excites.sapelli.storage.util.InvalidValueException;

/**
 * Class representing a reference to another {@link Record}, identified by the value(s) of its primary key.
 * This is equivalent to a foreign key, as used to reference a record of another ("foreign") schema.
 * 
 * Implemented as {@link RecordValueSet}, with an {@link PrimaryKey} instance (i.e. the primary key of the referenced, or "foreign" schema) as its {@link ColumnSet}.
 * 
 * @author mstevens
 */
public class RecordReference extends RecordValueSet<PrimaryKey>
{
	
	static private final long serialVersionUID = 2L;
	
	private final Schema referencedSchema;
	
	/**
	 * Creates a new, but "empty", RecordReference which, once the column values have been set, can be used to reference a record of the given schema. 
	 * 
	 * @param referencedSchema (also called "foreign" schema)
	 * @throws NullPointerException	if the recordSchema does not have a primary key
	 */
	protected RecordReference(Schema referencedSchema) throws NullPointerException
	{
		super(referencedSchema.getPrimaryKey());
		this.referencedSchema = referencedSchema;
	}
	
	/**
	 * Creates a new RecordReference, to be used for referencing a record of the given schema, which is initialised with the given key part values. 
	 * 
	 * @param referencedSchema (also called "foreign" schema)
	 * @param keyPartValues to initialise the recordReference, the number of values must match number of columns in primary key of the referencedSchema
	 * @throws IllegalArgumentException in case of an incorrect number of values
	 * @throws InvalidValueException in case of an invalid value
	 * @throws NullPointerException if the referencedSchema does not have a primary key, of if one of the keyPartValues is null
	 * @throws ClassCastException when a value cannot be converted/casted to the column's type {@code <T>}
	 */
	protected RecordReference(Schema referencedSchema, Object... keyPartValues) throws IllegalArgumentException, InvalidValueException, NullPointerException, ClassCastException
	{
		super(referencedSchema.getPrimaryKey(), keyPartValues); // We use the recordSchema's primary key as the schema for this record (i.e. for the recordReference, which is "a record" in its own right)
		this.referencedSchema = referencedSchema;
	}
	
	/**
	 * Creates a new RecordReference, to be used for referencing a record of the given schema, which is initialised with the given serialised key part values.
	 * 
	 * @param referencedSchema (also called "foreign" schema)
	 * @param serialisedKeyPartValues to initialise the recordReference, the number of values must match number of columns in primary key of the referencedSchema
	 * @throws NullPointerException	if the referencedSchema does not have a primary key
	 * @throws Exception when parsing serialisedValues fails
	 */
	protected RecordReference(Schema referencedSchema, String serialisedKeyPartValues) throws NullPointerException, Exception
	{
		super(referencedSchema.getPrimaryKey(), serialisedKeyPartValues);
		this.referencedSchema = referencedSchema;
	}
	
	/**
	 * Creates a new RecordReference, to be used for referencing a record of the given schema, which is initialised with the given serialised key part values.
	 * 
	 * @param referencedSchema (also called "foreign" schema)
	 * @param serialisedKeyPartValues byte array to initialise the recordReference with, the number of values must match number of columns in primary key of the referencedSchema, and all values must be encoded losslessly
	 * @throws NullPointerException	if the referencedSchema does not have a primary key
	 * @throws IOException when reading serialisedValues fails
	 */
	protected RecordReference(Schema referencedSchema, byte[] serialisedKeyPartValues) throws NullPointerException, IOException
	{
		super(referencedSchema.getPrimaryKey(), serialisedKeyPartValues, true /*always lossless, as values are part of PK!*/);
		this.referencedSchema = referencedSchema;
	}

	/**
	 * Creates a new RecordReference which points to the given {@link Record}.
	 * 
	 * @param record the Record to be referenced ("pointed to"), also called the "foreign" record
	 * @throws NullPointerException	if the Schema of the given Record does not have a primary key
	 * @throws IncompletePrimaryKeyException if (part of) the primary key column(s) lacks a value
	 */
	public RecordReference(Record record) throws NullPointerException, IncompletePrimaryKeyException
	{
		this(record, false); // don't allow blanks!
	}
	
	/*package*/ /**
	 * @param record
	 * @param allowBlank if {@code true}, {@code null} key part values are allowed (note this will produce a RecordReference instance that cannot be used for querying RecordStores directly) 
	 * @throws NullPointerException
	 * @throws IncompletePrimaryKeyException when the columns that are part of the primary key have not all been assigned a value, and {@code allowBlanks} is {@code false}
	 */
	RecordReference(Record record, boolean allowBlank) throws NullPointerException, IncompletePrimaryKeyException
	{
		this(record.getSchema()); // !!!
		
		// Copy the key part values:
		for(Column<?> keyPartCol : this.columnSet.getColumns(false))
		{
			Object keyPartValue = keyPartCol.retrieveValueCopy(record);
			if(!allowBlank && keyPartValue == null)
				throw new IncompletePrimaryKeyException("Cannot construct RecordReference from record because key part \"" + keyPartCol.getName() + "\" has not been set");
			setValue(keyPartCol, keyPartValue);
		}
	}
	
	/**
	 * Copy constructor.
	 * 
	 * @param another
	 */
	public RecordReference(RecordReference another)
	{
		super(another); // sets schema and copies values
		this.referencedSchema = another.referencedSchema;
	}
	
	/**
	 * @return the referencedSchema
	 */
	public Schema getReferencedSchema()
	{
		return referencedSchema;
	}

	@Override
	protected Schema getSchema()
	{
		return getReferencedSchema();
	}
	
	/**
	 * Returns the RecordReference itself.
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet#getReference()
	 */
	@Override
	public RecordReference getReference()
	{
		return this;
	}
	
	/**
	 * Returns a {@link Constraint} that matches on the referenced record's primary key values.
	 * 
	 * @param allowBlanks
	 * @return a Constraint
	 * @throws IncompletePrimaryKeyException when the columns that are part of the primary key (and thus covered by a recordReference) have not all been assigned a value, and {@code allowBlanks} is {@code false}
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet#getRecordQueryConstraint()
	 * @see uk.ac.ucl.excites.sapelli.storage.model.RecordValueSet#getRecordQueryConstraint(boolean)
	 */
	@Override
	/*package*/ Constraint getRecordQueryConstraint(boolean allowBlanks) throws IncompletePrimaryKeyException
	{
		if(!allowBlanks && !isFilled())
			throw new IncompletePrimaryKeyException("All values of the key must be set before a record selecting constraint/query can be created!");
		
		// Match for key parts:
		AndConstraint constraints = new AndConstraint();
		int c = 0;
		for(Object keyPart : values)
			constraints.addConstraint(new EqualityConstraint(columnSet.getColumn(c++), keyPart));
		
		return constraints.reduce();
	}
	
	@Override
	public int hashCode()
	{
		int hash = super.hashCode();
		hash = 31 * hash + referencedSchema.hashCode();
		return hash;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		if(obj instanceof RecordReference)
			return	super.equals(obj) &&
					this.referencedSchema.equals(((RecordReference) obj).referencedSchema);
		return false;
	}
	
}
