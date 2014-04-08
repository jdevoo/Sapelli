/**
 * 
 */
package uk.ac.ucl.excites.sapelli.storage.visitors;

import uk.ac.ucl.excites.sapelli.storage.model.RecordColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.BooleanColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.DateTimeColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.FloatColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.ForeignKeyColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerListColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.LineColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.LocationColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.OrientationColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.PolygonColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;

/**
 * @author mstevens
 *
 */
public interface ColumnVisitor
{
	
	public void visit(BooleanColumn boolCol);
	
	public void visit(DateTimeColumn dateTimeCol);
	
	public void visit(FloatColumn floatCol);
	
	public void visit(IntegerColumn intCol);
		
	public void visit(StringColumn stringCol);
	
	public void visit(IntegerListColumn intListCol);
	
	public void visit(LineColumn lineCol);
	
	public void visit(PolygonColumn polyCol);
	
	public void visit(ForeignKeyColumn foreignKeyCol);
	
	public void visit(LocationColumn locCol);
	
	public void visit(OrientationColumn orCol);
	
	public void enter(RecordColumn<?> recordCol);
	
	public void leave(RecordColumn<?> recordCol);
	
	public boolean allowLocationSelfTraversal();
	
	public boolean allowOrientationSelfTraversal();
	
	public boolean allowForeignKeySelfTraversal();
	
	public boolean skipNonBinaryStoredLocationColumns();
	
	public boolean skipNonBinaryStoredOrientationColumns();
	
	public boolean includeVirtualColumns();
		
}
