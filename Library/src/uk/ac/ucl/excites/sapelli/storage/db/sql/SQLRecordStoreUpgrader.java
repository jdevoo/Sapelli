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

package uk.ac.ucl.excites.sapelli.storage.db.sql;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.ucl.excites.sapelli.shared.db.StoreBackupper;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.db.sql.SQLRecordStore.TableFactory;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Model;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.indexes.PrimaryKey;

/**
 * @author mstevens
 *
 */
public abstract class SQLRecordStoreUpgrader
{
	
	private final List<String> warnings;
	protected final UpgradeCallback callback;
	protected final File backupFolder;
	private final Map<Integer, UpgradeStep<?>> steps;
	
	@SafeVarargs
	public SQLRecordStoreUpgrader(UpgradeCallback callback, File backupFolder, UpgradeStep<?>... steps)
	{
		this.warnings = new ArrayList<String>();
		this.callback = callback;
		this.backupFolder = backupFolder;
		this.steps = new HashMap<Integer, UpgradeStep<?>>(steps != null ? steps.length : 0);
		if(steps != null)
			for(UpgradeStep<?> step : steps)
				this.steps.put(step.fromVersion, step);
	}
	
	/**
	 * To be called from {@link RecordStore#initialise(boolean, int, Upgrader)}.
	 *  
	 * @param recordStore
	 * @param toVersion
	 * @throws DBException
	 */
	public final void upgrade(SQLRecordStore<?, ?, ?> recordStore, final int toVersion) throws DBException
	{
		final int fromVersion = recordStore.getVersion();
		
		// Clear previous warnings:
		warnings.clear();
		
		// Enable logging:
		boolean wasLoggingEnabled = recordStore.isLoggingEnabled();
		recordStore.setLoggingEnabled(true);
		
		// Apply step-by-step upgrade:
		int currentVersion;
		while((currentVersion = recordStore.getVersion()) < toVersion)
		{
			// Get the right upgrade step:
			UpgradeStep<?> step = steps.get(currentVersion);
			if(step == null)
				throw new DBException("No UpgradeStep for current version (" + currentVersion + ") found!");
			
			// Backup first:
			try
			{
				StoreBackupper.Backup(FileHelpers.getSubDirectory(backupFolder, "v" + currentVersion, true), true, recordStore);
			}
			catch(Exception e)
			{
				throw new DBException("Database backup prior to upgrade from v" + currentVersion + " to v" + step.toVersion + " failed!", e);
			}
			
			// Apply upgrade step (always in a transaction):
			try
			{
				// Open transaction:
				recordStore.startTransaction();
				// Apply step:
				step.apply(recordStore, new UpgradeOperations());
				// Set new version:
				recordStore.setVersion(step.toVersion);
				// Close transaction:
				recordStore.commitTransaction();
				// Run clean-up before continuing with next upgrade step or completing upgrade:
				recordStore.cleanup();
			}
			catch(Exception e)
			{
				// don't roll back here, SQLRecordStore#initialise() will do that
				throw new DBException("Failed to upgrade database from v" + currentVersion + " to v" + step.toVersion + "!", e);
			}
		}
		
		// Re-disable logging if needed:
		recordStore.setLoggingEnabled(wasLoggingEnabled);
		
		// If successful:
		if(callback != null)
			callback.upgradePerformed(fromVersion, toVersion, warnings);
	}
	
	/**
	 * @author mstevens
	 */
	static public abstract class UpgradeStep<C extends StorageClient>
	{
		
		protected final C client;
		public final int fromVersion;
		public final int toVersion;
		
		public UpgradeStep(C client, int fromVersion)
		{
			this(client, fromVersion, fromVersion + 1);
		}
		
		public UpgradeStep(C client, int fromVersion, int toVersion)
		{
			this.client = client;
			this.fromVersion = fromVersion;
			this.toVersion = toVersion;
		}
		
		public abstract void apply(SQLRecordStore<?, ?, ?> recordStore, UpgradeOperations upgradeOperations) throws Exception;
		
	}
	
	/**
	 * Helper class which grants UpgradeSteps access to a number of {@link SQLRecordStore} methods would otherwise be
	 * inaccessible due to their package or protected access level.
	 * 
	 * @author mstevens
	 */
	public class UpgradeOperations
	{
		
		/**
		 * @param unsanitisedTableName
		 * @see SQLRecordStore#doesTableExist(String)
		 */
		public boolean doesTableExist(SQLRecordStore<?, ?, ?> recordStore, String unsanitisedTableName)
		{
			return recordStore.doesTableExist(unsanitisedTableName);
		}
		
		/**
		 * @see SQLRecordStore#sanitiseIdentifier(String)
		 */
		public String sanitiseIdentifier(SQLRecordStore<?, ?, ?> recordStore, String identifier)
		{
			return recordStore.sanitiseIdentifier(identifier);
		}
		
		/**
		 * @see SQLRecordStore#forgetTable(String)
		 */
		public void forgetTable(SQLRecordStore<?, ?, ?> recordStore, String unsanitisedTableName) throws DBException
		{
			recordStore.forgetTable(unsanitisedTableName);
		}
		
		/**
		 * @see SQLRecordStore#dropTable(String, boolean)
		 */
		public void dropTable(SQLRecordStore<?, ?, ?> recordStore, String unsanitisedTableName, boolean force) throws DBException
		{
			recordStore.dropTable(unsanitisedTableName, force);
		}
		
		/**
		 * @param oldTableName - unsanitised!
		 * @param newTableName - unsanitised!
		 * @see SQLRecordStore#renameTable(String, String)
		 */
		public void renameTable(SQLRecordStore<?, ?, ?> recordStore, String oldTableName, String newTableName) throws DBException
		{
			recordStore.renameTable(oldTableName, newTableName);
		}
		
		/**
		 * @return a {@link List} of the (unsanitised!) names of all tables in the database
		 * @see SQLRecordStore#getAllTableNames()
		 */
		public List<String> getAllTableNames(SQLRecordStore<?, ?, ?> recordStore)
		{
			return recordStore.getAllTableNames();
		}
		
		/**
		 * @see SQLRecordStore#getAllKnownSchemata()
		 */
		public List<Schema> getAllSchemata(SQLRecordStore<?, ?, ?> recordStore)
		{
			return recordStore.getAllKnownSchemata();
		}
		
		/**
		 * @see SQLRecordStore#getTableFactory()
		 */
		public TableFactory<?> getTableFactory(SQLRecordStore<?, ?, ?> recordStore)
		{
			return recordStore.getTableFactory();
		}
		
		/**
		 * Calls {@link SQLRecordStore#cleanup()}.
		 * 
		 * Note that this method is already called after completion of each upgrade step, so UpgradeSteps
		 * should only call this themselves in case they require a cleanup to happen during the step itself.
		 * 
		 * @see SQLRecordStore#cleanup()
		 */
		public void cleanup(SQLRecordStore<?, ?, ?> recordStore) throws DBException
		{
			recordStore.cleanup();
		}
		
		/**
		 * @see SQLRecordStore#release()
		 */
		public void release(SQLRecordStore<?, ?, ?> recordStore)
		{
			recordStore.release();
		}
		
		public void addWarning(String warning)
		{
			warnings.add(warning);
		}

		public void addWarnings(Collection<String> warnings)
		{
			SQLRecordStoreUpgrader.this.warnings.addAll(warnings);
		}
		
	}
	
	/**
	 * @author mstevens
	 */
	static public class TableConverter
	{
		
		protected final Model newModel;
		protected final Schema newSchema;
		protected final int oldSchemaFlags;
		protected List<ColumnReplacer> columnReplacers = new ArrayList<ColumnReplacer>();
		
		public TableConverter(Schema newSchema)
		{
			this(newSchema, newSchema.flags); // flags are unchanged between new & old schema
		}
		
		public TableConverter(Schema newSchema, int oldSchemaFlags)
		{
			this.newSchema = newSchema;
			this.oldSchemaFlags = oldSchemaFlags;
			this.newModel = newSchema.model;
		}
		
		public Schema getNewSchema()
		{
			return newSchema;
		}
		
		public Schema getOldSchema()
		{
			if(isTransparent())
				return newSchema;
			// else:
			// Construct a fake recreation of the Schema (and its Model) with "v1x" MediaField columns, this "oldSchema" should be compatible with table as it currently exists in the database:
			Model oldModel;
			if(newModel.hasDefaultSchemaFlags())
				oldModel = new Model(newModel.id, newModel.name, newModel.getDefaultSchemaFlags());
			else
				oldModel = new Model(newModel.id, newModel.name);
			
			// Insert fake versions of the schemata occurring in the Model before the one we care about:
			for(int s = 0; s < newSchema.modelSchemaNumber; s++)
				new Schema(oldModel, "Fake_" + s, "FakeTable_" + s, 0);
			
			// Create (& insert into the oldModel) a replica of the newSchema, with the old version of the columns that have been changed:
			Schema oldSchema = new Schema(oldModel, newSchema.getName(), newSchema.tableName, oldSchemaFlags);
			for(Column<?> newColumn : newSchema.getColumns(false))
			{
				ColumnReplacer cr = getColumnReplacer(newColumn);
				if(cr == null)
					// Column is the same in new and old schema:
					oldSchema.addColumn(newColumn);
				else
				{
					Column<?> oldColumn = cr.getOldColumn(newColumn);
					if(oldColumn != null)
						oldSchema.addColumn(oldColumn);
				}
			}
			
			// Set PK & seal oldSchema:
			if(newSchema.hasPrimaryKey())
			{
				PrimaryKey newPK = newSchema.getPrimaryKey();
				PrimaryKey oldPK;
				if(newPK instanceof AutoIncrementingPrimaryKey)
				{
					IntegerColumn newAutoIncrPKCol = ((AutoIncrementingPrimaryKey) newPK).getColumn();
					ColumnReplacer cr = getColumnReplacer(newAutoIncrPKCol);
					oldPK = new AutoIncrementingPrimaryKey(newPK.getName(), cr == null ? newAutoIncrPKCol : (IntegerColumn) cr.getOldColumn(newAutoIncrPKCol));
				}
				else
				{
					List<Column<?>> oldPKCols = new ArrayList<Column<?>>();
					for(Column<?> newPKCol : newPK.getColumns(false))
					{
						ColumnReplacer cr = getColumnReplacer(newPKCol);
						oldPKCols.add(cr == null ? newPKCol : cr.getOldColumn(newPKCol));
					}
					oldPK = new PrimaryKey(newPK.getName(), oldPKCols.toArray(new Column<?>[oldPKCols.size()]));
				}
				oldSchema.setPrimaryKey(oldPK, true); // and seal
			}
			else
				oldSchema.seal();
			// Note: non-PK indexes are no replicated, this should not be necessary to query the old table.
			
			// Insert fake versions of the schemata occurring in the Model after the one we care about:
			while(oldModel.getNumberOfSchemata() < newModel.getNumberOfSchemata())
				new Schema(oldModel, "Fake_" + oldModel.getNumberOfSchemata(), "FakeTable_" + oldModel.getNumberOfSchemata(), 0);
			
			// Seal oldModel:
			oldModel.seal();
			
			// Done:
			return oldSchema;
		}
		
		public List<Record> convertRecords(List<Record> oldRecords)
		{
			if(isTransparent())
				return oldRecords;
			// else:
			List<Record> newRecords = new ArrayList<Record>(oldRecords.size());
			for(Record oldRecord : oldRecords)
			{
				// Create new record:
				Record newRecord = newSchema.createRecord();
				newRecords.add(newRecord);
				// Copy or convert values:
				for(Column<?> newColumn : newSchema.getColumns(false))
				{
					ColumnReplacer cr = getColumnReplacer(newColumn);
					if(cr == null)
						newColumn.copyValue(oldRecord, newRecord);
					else
						newColumn.storeObject(newRecord, cr.convertValue(newColumn, oldRecord));
				}
			}
			return newRecords;
		}
		
		/**
		 * @param cr
		 */
		public void addColumnReplacer(ColumnReplacer cr)
		{
			columnReplacers.add(cr);
		}
		
		public boolean isTransparent()
		{
			return columnReplacers.isEmpty() && newSchema.flags == oldSchemaFlags;
		}
		
		/**
		 * @param newColumn - must be a real (non-virtual) and top-level column! We don't yet support direct replacing of subcolumns of ValueSetColumns or singleColumn of ListColumns
		 * @return a {@link ColumnReplacer} instance matching the given new column, or {@code null} if the given column is unchanged from the old schema
		 */
		public ColumnReplacer getColumnReplacer(Column<?> newColumn)
		{
			for(ColumnReplacer cr : columnReplacers)
				if(cr.matches(newColumn))
					return cr;
			return null;
		}
		
	}
	
	/**
	 * @author mstevens
	 */
	static public abstract class ColumnReplacer
	{
		
		/**
		 * @param newColumn - must be a real (non-virtual) and top-level column! We don't yet support direct replacing of subcolumns of ValueSetColumns or singleColumn of ListColumns
		 */
		public abstract boolean matches(Column<?> newColumn);
		
		/**
		 * @return the old version of the given column, or {@code null} if the new column is entirely new (only allowed for non-PK columns!)
		 */
		protected abstract Column<?> getOldColumn(Column<?> newColumn);
		
		/**
		 * By default values are not converted, just copied over.
		 * Subclasses must override this to convert values.
		 * 
		 * @param newColumn
		 * @param oldRecord
		 * @return
		 */
		protected Object convertValue(Column<?> newColumn, Record oldRecord)
		{
			return getOldColumn(newColumn).retrieveValue(oldRecord);
		}
		
	}
	
	/**
	 * @author mstevens
	 */
	static public class DefaultValueColumnAdder extends ColumnReplacer
	{
		
		private final Column<?> newColumn;
		
		/**
		 * @param newColumn
		 */
		public DefaultValueColumnAdder(Column<?> newColumn)
		{
			this.newColumn = newColumn;
		}

		@Override
		public boolean matches(Column<?> newColumn)
		{
			return this.newColumn == newColumn;
		}

		@Override
		protected Column<?> getOldColumn(Column<?> newColumn)
		{
			return null; // there is no old version of this column, it is entirely new
		}

		@Override
		protected Object convertValue(Column<?> newColumn, Record oldRecord)
		{
			return this.newColumn.defaultValue;
		}
		
	}
	
	/**
	 * @author mstevens
	 */
	public interface UpgradeCallback
	{
		
		public void upgradePerformed(int fromVersion, int toVersion, List<String> warnings);
		
	}
	
}
