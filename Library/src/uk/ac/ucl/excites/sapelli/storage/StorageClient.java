/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory;
import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory.Compression;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreCreator;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.StreamHelpers;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.util.UnknownModelException;

/**
 * TODO upwards error logging mechanism 
 * 
 * @author mstevens
 */
public abstract class StorageClient implements StorageObserver
{

	// STATICS ------------------------------------------------------
	static public enum RecordOperation
	{
		Inserted,
		Updated,
		Deleted
	}
	
	
	static private Compression MODEL_SERIALISATION_COMPRESSION = Compression.DEFLATE;
	static protected final byte[] MODEL_SERIALISATION_HEADER_BYTES = "SapelliModel".getBytes(Charset.forName("UTF-8"));
	static protected final byte MODEL_SERIALISATION_KIND_RESERVED = 0;
	static protected final byte MODEL_SERIALISATION_KIND_COMPRESSED_JAVA_OBJECT = -1;
	
	// DYNAMICS -----------------------------------------------------
	private final List<StorageObserver> observers = new LinkedList<StorageObserver>();
	
	public final StoreHandle<RecordStore> recordStoreHandle = new StoreHandle<RecordStore>(new StoreCreator<RecordStore>()
	{
		@Override
		public RecordStore createStore() throws DBException
		{
			return createRecordStore();
		}
	});
	
	/**
	 * @param modelID
	 * @return
	 * @throws UnknownModelException
	 */
	public final Model getModel(long modelID) throws UnknownModelException
	{
		// First check reserved models:
		Model reservedModel = getReservedModel(modelID);
		if(reservedModel != null)
			return reservedModel;
		// Get client model:
		return getClientModel(modelID);
	}
	
	private Model getReservedModel(long modelID)
	{
		for(Model model : getReservedModels())
			if(model.getID() == modelID)
				return model;
		return null;
	}
	
	/**
	 * Subclasses can override this but *must* return at least the same models returned by the super implementation.
	 * 
	 * @return
	 */
	protected List<Model> getReservedModels()
	{
		return new ArrayList<Model>();
	}
	
	/**
	 * @param modelID
	 * @return
	 * @throws UnknownModelException
	 */
	protected abstract Model getClientModel(long modelID) throws UnknownModelException;
	
	/**
	 * Converts {@link Model} instances into byte[] representations.
	 * 
	 * @param model the {@link Model} instance to serialise
	 * @return a byte[] containing the serialised model
	 * @throws Exception
	 */
	public final byte[] serialiseModel(Model model) throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			// Write header:
			out.write(MODEL_SERIALISATION_HEADER_BYTES);
			
			// Check if this is a reserved model:
			if(getReservedModel(model.id) != null)
			{
				// Write kind byte:
				out.write(MODEL_SERIALISATION_KIND_RESERVED);
				// Write model id:
				BitOutputStream bitOut = new BitWrapOutputStream(out);
				Model.MODEL_ID_FIELD.write(model.id, bitOut);
				bitOut.flush();
				bitOut.close();
			}
			else
			{	// This is not a reserved model, ask the subclass to serialise it:
				try
				{
					serialiseClientModel(model, out); // is assumed to close the stream!
				}
				catch(UnknownModelException uke)
				{	// Use compressed Java object serialisation instead:
					out.write(MODEL_SERIALISATION_KIND_COMPRESSED_JAVA_OBJECT);
					ObjectOutputStream objOut = new ObjectOutputStream(compress(out));
					objOut.writeObject(model);
					objOut.flush();
					objOut.close();
				}
			}
			
			// Return bytes:
			return out.toByteArray();
		}
		finally
		{	// Just in case it hasn't been closed yet:
			StreamHelpers.SilentClose(out);
		}
	}
	
	/**
	 * Converts serialised models (given as a byte[]) back to a {@link Model} instance.
	 * 
	 * @param serialisedModel a byte[] which is the result of serialising a Model instance using {@link #serialiseModel(Model)}
	 * @return the deserialised Model instance
	 * @throws Exception in case deserialisation failed
	 */
	public final Model deserialiseModel(byte[] serialisedModel) throws Exception
	{		
		InputStream in = new ByteArrayInputStream(serialisedModel);
		try
		{
			// Check for "SapelliModel" header:
			in.mark(MODEL_SERIALISATION_HEADER_BYTES.length);
			byte[] headerBytes = new byte[MODEL_SERIALISATION_HEADER_BYTES.length];
			if(	in.read(headerBytes) == MODEL_SERIALISATION_HEADER_BYTES.length &&
				Arrays.equals(headerBytes, MODEL_SERIALISATION_HEADER_BYTES))
			{	// Header is present, read which kind of model serialisation is used:
				byte kind = (byte) in.read();
				// Deserialise according to kind:
				switch(kind)
				{
					case MODEL_SERIALISATION_KIND_RESERVED :
						return getReservedModel(Model.MODEL_ID_FIELD.readLong(new BitWrapInputStream(in)));
					case MODEL_SERIALISATION_KIND_COMPRESSED_JAVA_OBJECT :
						return deserialiseCompressedModelObject(in);
					default :
						return deserialiseClientModel(kind, in); // may throw and exception or return null if model was unknown
				}
			}
			else
			{	// No header present, this model is serialised in the old way (headerless, compressed Java object serialisation):
				in.reset();
				return deserialiseCompressedModelObject(in);
			}
		}
		finally
		{
			StreamHelpers.SilentClose(in);
		}
	}
	
	/**
	 * @param in
	 * @return in the {@link InputStream} to read from, will be closed
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	private Model deserialiseCompressedModelObject(InputStream in) throws ClassNotFoundException, IOException
	{
		try
		{
			in = new ObjectInputStream(decompress(in));
			return (Model) ((ObjectInputStream) in).readObject();
		}
		finally
		{
			StreamHelpers.SilentClose(in);
		}
	}
	
	/**
	 * To be implemented by subclasses in order to serialise their specific Model kinds.
	 * 
	 * If the subclass is able to serialise the model it *must* start with writing a single "kind" byte
	 * to the output, which signifies the serialisation kind or format and should be understood by the
	 * subclass' implementation of {@link #deserialiseClientModel(byte, InputStream)}. 
	 * 
	 * @param model the {@link Model} instance to serialise
	 * @param out the {@link OutputStream} to write to, must be closed before this method returns!
	 * @throws IOException in case a problem occurs during serialisation
	 * @throws UnknownModelException in case the given Model is not recognised or serialisable by the subclass 
	 */
	protected abstract void serialiseClientModel(Model model, OutputStream out) throws IOException, UnknownModelException;
	
	/**
	 * To be implemented by subclasses in order to deserialise their specific Model kinds.
	 * 
	 * @param kind byte indicating the kind of model/model serialisation 
	 * @param in the {@link InputStream} to read from, must be closed before this method returns!
	 * @return the deserialised Model, or {@code null} if the model was unknown
	 * @throws Exception if something when wrong
	 */
	protected abstract Model deserialiseClientModel(byte kind, InputStream in) throws Exception;
	
	/**
	 * Helper method for Model serialisation. Wraps a given OutputStream in a compressing one.
	 * 
	 * @param out
	 * @return
	 * @throws IOException
	 */
	protected final OutputStream compress(OutputStream out) throws IOException
	{
		return CompressorFactory.getCompressorOutputStream(MODEL_SERIALISATION_COMPRESSION, out);
	}
	
	/**
	 * Helper method for Model deserialisation. Wraps a given InputStream in a decompressing one.
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	protected final InputStream decompress(InputStream in) throws IOException
	{
		return CompressorFactory.getCompressorInputStream(MODEL_SERIALISATION_COMPRESSION, in);
	}
	
	/**
	 * @param modelID
	 * @param schemaNumber
	 * @return
	 * @throws UnknownModelException when no model with the given {@code modelID} was found
	 * @throws IndexOutOfBoundsException when the model with the given {@code modelID} does not have a schema with the given {@code schemaNumber}
	 */
	public Schema getSchema(long modelID, int schemaNumber) throws UnknownModelException, IndexOutOfBoundsException
	{
		return getSchema(modelID, schemaNumber, null);
	}
	
	/**
	 * @param modelID
	 * @param schemaNumber
	 * @param schemaName may be null; is not checked against returned Schema(!), only used to pass to UnknownModelException in case no model is found 
	 * @return a matching Schema
	 * @throws UnknownModelException when no model with the given {@code modelID} was found
	 * @throws IndexOutOfBoundsException when the model with the given {@code modelID} does not have a schema with the given {@code schemaNumber}
	 */
	public Schema getSchema(long modelID, int schemaNumber, String schemaName) throws UnknownModelException, IndexOutOfBoundsException
	{
		try
		{
			return getModel(modelID).getSchema(schemaNumber);
		}
		catch(UnknownModelException ume)
		{
			throw new UnknownModelException(modelID, null, schemaNumber, schemaName); // throw UME with schema information instead of only modelID
		}
	}
	
	/**
	 * Returns the name to be used for a table which will contain records of the given schema in
	 * back-end (relational) database storage (i.e. through a RecordStore implementation).
	 * 
	 * May be overridden by subclasses to add additional exceptional cases.
	 * 
	 * TODO replace this and the overriding methods by a more elegant mechanism using the Schema.name (and perhaps making it plural)
	 * 
	 * @return
	 */
	public String getTableName(Schema schema)
	{
		if(schema == Model.MODEL_SCHEMA)
			return "Models";
		if(schema == Model.META_SCHEMA)
			return "Schemata";
		else
			return "Table_" + schema.getModelID() + '_' + schema.getModelSchemaNumber(); // we don't use schema#name to avoid name clashes and illegal characters
	}
	
	/**
	 * @param schemaID
	 * @param schemaVersion
	 * @return a matching {@link Schema} instance
	 * @throws {@link UnknownModelException} when no matching schema is found
	 */
	public abstract Schema getSchemaV1(int schemaID, int schemaVersion) throws UnknownModelException;
	
	public final void addObserver(StorageObserver observer)
	{
		if(observer != null)
			this.observers.add(observer);
	}
	
	@Override
	public final void storageEvent(RecordOperation operation, RecordReference recordRef)
	{
		// Forward to all observers (if any):
		for(StorageObserver observer : observers)
			observer.storageEvent(operation, recordRef);
	}
	
	/**
	 * TODO this can be problematic, Transmission client needs to know which records...
	 * TODO remove
	 */
	public void recordsDeleted(RecordsQuery query, int numberOfDeletedRecords) {};
	
	/**
	 * Returns a new RecordStore instance
	 * 
	 * @return
	 * @throws DBException
	 */
	protected abstract RecordStore createRecordStore() throws DBException;
	
}
