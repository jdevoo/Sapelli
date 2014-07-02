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

package uk.ac.ucl.excites.sapelli.storage.queries.constraints;

import uk.ac.ucl.excites.sapelli.storage.model.Record;

/**
 * Negation of a Constraint
 * 
 * @author mstevens
 */
public class NotConstraint extends Constraint
{
	
	/**
	 * @param constraintToNegate
	 * @return
	 */
	static Constraint Negate(Constraint constraintToNegate)
	{
		if(constraintToNegate instanceof NotConstraint)
			// Avoid double-negations:
			return ((NotConstraint) constraintToNegate).negatedConstraint;
		else
			return new NotConstraint(constraintToNegate);
	}

	private final Constraint negatedConstraint;
	
	private NotConstraint(Constraint negatedConstraint)
	{
		if(negatedConstraint == null)
			throw new NullPointerException("negatedConstraint cannot be null!");
		this.negatedConstraint = negatedConstraint;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint#_isValid(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	protected boolean _isValid(Record record)
	{
		return !negatedConstraint._isValid(record);
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint#accept(uk.ac.ucl.excites.sapelli.storage.queries.constraints.ConstraintVisitor)
	 */
	@Override
	public void accept(ConstraintVisitor visitor)
	{
		visitor.visit(this);
	}
	
	/**
	 * @return the negatedConstraint
	 */
	public Constraint getNegatedConstraint()
	{
		return negatedConstraint;
	}

}
