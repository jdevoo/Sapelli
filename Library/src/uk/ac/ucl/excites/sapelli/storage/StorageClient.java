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

package uk.ac.ucl.excites.sapelli.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory;
import uk.ac.ucl.excites.sapelli.shared.compression.CompressorFactory.Compression;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreCreator;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreOperationWithReturnNoException;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreSetter;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitWrapOutputStream;
import uk.ac.ucl.excites.sapelli.shared.io.StreamHelpers;
import uk.ac.ucl.excites.sapelli.shared.util.Console;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.Attachment;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.util.UnknownModelException;

/**
 * @author mstevens
 */
public abstract class StorageClient implements StorageObserver, Console
{

	// STATICS ------------------------------------------------------
	static public enum RecordOperation
	{
		Inserted,
		Updated,
		Deleted
	}
	
	static private final Map<Long, Model> RESERVED_MODELS = new HashMap<Long, Model>();
	
	static public final Collection<Model> GetReservedModels()
	{
		return Collections.unmodifiableCollection(RESERVED_MODELS.values());
	}
	
	static final protected void AddReservedModel(Model newReservedModel)
	{
		if(newReservedModel == null)
			throw new NullPointerException("newReservedModel cannot be null!");
		if(RESERVED_MODELS.containsKey(newReservedModel.id) && RESERVED_MODELS.get(newReservedModel.id) != newReservedModel)
			throw new IllegalStateException("Reserved model id clash (id: " + newReservedModel.id + ")!");
		RESERVED_MODELS.put(newReservedModel.id, newReservedModel);
	}
	
	/**
	 * @param modelID
	 * @return a reserved Model with the given modelID, or {@code null} if no such reserved Model is known
	 */
	static final protected Model GetReservedModel(long modelID)
	{
		return RESERVED_MODELS.get(modelID);
	}
	
	static private Compression MODEL_SERIALISATION_COMPRESSION = Compression.DEFLATE;
	static protected final byte[] MODEL_SERIALISATION_HEADER_BYTES = "SapelliModel".getBytes(Charset.forName("UTF-8"));
	static protected final byte MODEL_SERIALISATION_KIND_RESERVED = 0;
	static protected final byte MODEL_SERIALISATION_KIND_COMPRESSED_JAVA_OBJECT = -1;
	
	/**
	 * Schema flag indicating that the Schema has been defined at the Storage layer of the Sapelli Library.
	 * 
	 * Note: flag bits 4 & 5 are reserved for future Storage layer usage.
	 */
	static private final int SCHEMA_FLAG_STORAGE_LAYER =	1 << 0;
	
	/**
	 * Schema flag indicating that records of the Schema are exportable.
	 */
	static public final int SCHEMA_FLAG_EXPORTABLE = 		1 << 1;
	
	/**
	 * Schema flag indicating that changes made to records of the Schema will reported to StorageObservers.
	 */
	static public final int SCHEMA_FLAG_TRACK_CHANGES =		1 << 2;

	/**
	 * Schema flag indicating that records of the Schema hold a history of changes.
	 * Using this flag implies the Schema will have change tracking (@see {@link #SCHEMA_FLAG_TRACK_CHANGES}) enabled as well.
	 * TODO Not yet implemented
	 */
	static public final int SCHEMA_FLAG_KEEP_HISTORY =		SCHEMA_FLAG_TRACK_CHANGES | 1 << 3;
	
	/**
	 * Schema flag indicating that records of the Schema keep track of their lossless- or lossy-ness.
	 */
	static public final int SCHEMA_FLAG_TRACK_LOSSLESSNESS = 1 << 4;
	
	// Note: flag bit 5 is reserved for future Storage layer usage
	
	/**
	 * Flags used on "internal" Storage layer Schemata.
	 */
	static public final int SCHEMA_FLAGS_STORAGE_INTERNAL =	SCHEMA_FLAG_STORAGE_LAYER;
	
	/**
	 * Method to test in the given int flags value matches the given flags (bit) pattern.
	 * 
	 * @param flagsValue
	 * @param flagsPattern
	 * @return whether or not all the flags in the flagsPattern are set in the flagsValue 
	 */
	static public boolean TestSchemaFlags(int flagsValue, int flagsPattern)
	{
		return (flagsValue & flagsPattern) == flagsPattern;
	}
	
	static private final Map<Integer, String> TABLENAME_PREFIXES = new LinkedHashMap<Integer, String>(); // using LinkedHashMap to preserve insertion order
	
	static protected void AddTableNamePrefix(int flagsPattern, String tableNamePrefix)
	{
		TABLENAME_PREFIXES.put(flagsPattern, tableNamePrefix);
	}
	
	/**
	 * Generates a complete table name for a Schema with the given name, flags, "unprefixed" basic table name (may be null), and table name suffix (may be null).
	 * 
	 * @param schemaName
	 * @param schemaFlags
	 * @param unprefixedTableName may be {@code null}, in which schemaName is used
	 * @param tableNameSuffix may be {@code null}
	 * @return
	 */
	static public String GetSchemaTableName(String schemaName, int schemaFlags, String unprefixedTableName, String tableNameSuffix)
	{
		if(schemaName == null && unprefixedTableName == null)
			return null;
		StringBuilder bldr = new StringBuilder();
		// Prefix(es):
		for(Map.Entry<Integer, String> entry : TABLENAME_PREFIXES.entrySet())
			if(TestSchemaFlags(schemaFlags, entry.getKey().intValue()))
				bldr.append(entry.getValue());
		// Table name (or Schema name):
		bldr.append(unprefixedTableName != null ? unprefixedTableName : schemaName);
		// Suffix:
		if(tableNameSuffix != null)
			bldr.append(tableNameSuffix);
		// Return full table name:
		return bldr.toString();
	}
	
	/**
	 * @param model cannot be null, will be used to provide the schema flags (if the Model doesn't have default schema flags a NullPointerException will be thrown)
	 * @param schemaName will also be used as tableName
	 * @return
	 */
	static public Schema CreateSchema(Model model, String schemaName)
	{
		return CreateSchema(model, schemaName, null, schemaName, null);
	}
	
	/**
	 * @param model cannot be null
	 * @param schemaName will also be used as tableName
	 * @return
	 */
	static public Schema CreateSchema(Model model, String schemaName, int schemaFlags)
	{
		return CreateSchema(model, schemaName, schemaFlags, schemaName, null);
	}
	
	/**
	 * @param model cannot be null, will be used to provide the schema flags (if the Model doesn't have default schema flags a NullPointerException will be thrown)
	 * @param schemaName
	 * @param unprefixedTableName if null the schemaName will be used instead
	 * @return
	 */
	static public Schema CreateSchema(Model model, String schemaName, String unprefixedTableName)
	{
		return CreateSchema(model, schemaName, null, unprefixedTableName, null);
	}
	
	/**
	 * @param model cannot be null
	 * @param schemaName
	 * @param schemaFlags
	 * @param unprefixedTableName if null the schemaName will be used instead
	 * @return
	 */
	static public Schema CreateSchema(Model model, String schemaName, int schemaFlags, String unprefixedTableName)
	{
		return CreateSchema(model, schemaName, schemaFlags, unprefixedTableName, null);
	}
	
	/**
	 * @param model cannot be null, will be used to provide the schema flags (if the Model doesn't have default schema flags a NullPointerException will be thrown)
	 * @param schemaName
	 * @param tableNameSuffix
	 * @return
	 */
	static public Schema CreateSchemaWithSuffixedTableName(Model model, String schemaName, String tableNameSuffix)
	{
		return CreateSchema(model, schemaName, null, schemaName, tableNameSuffix);
	}
	
	/**
	 * @param model cannot be null
	 * @param schemaName
	 * @param schemaFlags
	 * @param tableNameSuffix
	 * @return
	 */
	static public Schema CreateSchemaWithSuffixedTableName(Model model, String schemaName, int schemaFlags, String tableNameSuffix)
	{
		return CreateSchema(model, schemaName, schemaFlags, schemaName, tableNameSuffix);
	}
	
	/**
	 * @param model cannot be null
	 * @param schemaName
	 * @param schemaFlags may be null, in which case the default schema flags of the Model will be used (if it doesn't have any a NullPointerException will be thrown)
	 * @param unprefixedTableName if null the schemaName will be used instead
	 * @param tableNameSuffix may be null
	 * @return
	 */
	static protected Schema CreateSchema(Model model, String schemaName, Integer schemaFlags, String unprefixedTableName, String tableNameSuffix)
	{
		if(schemaFlags == null)
			schemaFlags = model.getDefaultSchemaFlags();
		return new Schema(model, schemaName, GetSchemaTableName(schemaName, schemaFlags, unprefixedTableName, tableNameSuffix), schemaFlags.intValue());
	}
	
	// DYNAMICS -----------------------------------------------------
	private final List<StorageObserver> observers = new LinkedList<StorageObserver>();
	
	public final StoreHandle<RecordStore> recordStoreHandle = new StoreHandle<RecordStore>(this, new StoreCreator<RecordStore>()
	{
		@Override
		public void createAndSetStore(StoreSetter<RecordStore> setter) throws DBException
		{
			createAndSetRecordStore(setter);
		}
	});
	
	/**
	 * Creates a new RecordStore instance
	 * 
	 * @param setter
	 * @throws DBException
	 */
	protected abstract void createAndSetRecordStore(StoreSetter<RecordStore> setter) throws DBException;
	
	/**
	 * @param record should not be {@code null}
	 * @return a {@link List} with {@link Attachment}s associated with the given record, note that the attachment files do *not* necessarily exist
	 */
	public abstract List<? extends Attachment> getRecordAttachments(Record record);
	
	/**
	 * @param modelID
	 * @return a {@link Model} instance with the given ID
	 * @throws UnknownModelException if no matching Model was found
	 */
	public final Model getModel(final long modelID) throws UnknownModelException
	{
		Model model = null;
		
		// Check reserved models:
		if((model = GetReservedModel(modelID)) != null)
			return model;
		
		// Get client model:
		if((model = getClientModel(modelID)) != null)
			return model;
		
		// Check models of stored records:
		if((model = recordStoreHandle.executeWithReturnNoDBEx(new StoreOperationWithReturnNoException<RecordStore, Model>()
		{
			@Override
			public Model execute(RecordStore store)
			{
				return store.retrieveModel(modelID);
			}
		})) != null)
			return model;
		
		// If we get here this means we really can't find a matching model:
		throw new UnknownModelException(modelID, null);
	}
	
	/**
	 * @param modelID
	 * @return a {@link Model} instance with the given ID, or {@code null} if no such model was found
	 */
	protected abstract Model getClientModel(long modelID);
	
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
			if(GetReservedModel(model.id) != null)
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
						return GetReservedModel(Model.MODEL_ID_FIELD.readLong(new BitWrapInputStream(in)));
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
	public final void storageEvent(RecordOperation operation, RecordReference recordRef, RecordStore recordStore)
	{
		if(	// Any events coming from an initialised RecordStore (this avoids forwarding events during db upgrades) and ...
			recordStore.isInitialised() &&
			// 	about records whose Schema has track changes enabled ...
			recordRef.getReferencedSchema().hasFlags(SCHEMA_FLAG_TRACK_CHANGES))
			// must be forwarded to all observers (if any):
			for(StorageObserver observer : observers)
				observer.storageEvent(operation, recordRef, recordStore);
	}
	
	@Override
	public final void logError(String msg)
	{
		logError(msg, null);
	}
	
	@Override
	public abstract void logError(String msg, Throwable throwable);
	
	@Override
	public abstract void logWarning(String msg);
	
	@Override
	public abstract void logInfo(String msg);
	
}
