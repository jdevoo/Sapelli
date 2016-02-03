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

package uk.ac.ucl.excites.sapelli.shared.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author mstevens
 *
 */
public interface WarningKeeper
{

	public void addWarning(String warning);
	
	public void addWarnings(Collection<String> warnings);

	public List<String> getWarnings();

	public void clearWarnings();
	
	/**
	 * @author mstevens
	 */
	public class WarningKeeperImpl implements WarningKeeper
	{
		
		private List<String> warnings;

		@Override
		public void addWarning(String warning)
		{
			if(warnings == null)
				warnings = new ArrayList<String>();
			warnings.add(warning);
		}

		@Override
		public void addWarnings(Collection<String> warnings)
		{
			if(this.warnings == null)
				this.warnings = new ArrayList<String>();
			this.warnings.addAll(warnings);
		}

		@Override
		public List<String> getWarnings()
		{
			return warnings != null ? warnings : Collections.<String> emptyList();
		}
		
		@Override
		public void clearWarnings()
		{
			warnings = null;
		}
		
	}
	
}
