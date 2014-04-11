/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Michalis Vitos, mstevens
 *
 */
public class Trigger implements JumpSource
{

	// Statics----------------------------------------------
	static public final int NO_TIMEOUT = -1;
	static public String KEY_SEPARATOR = "\\|";
	
	static public enum Key
	{
		ANY,
		BACK,
		SEARCH,
		HOME,
		VOLUME_DOWN,
		VOLUME_MUTE,
		VOLUME_UP,
		// more later?
	}
	
	// Dynamics---------------------------------------------
	protected List<Key> keys;
	protected int fixedTimer = NO_TIMEOUT;
	protected Field jump;
	protected FieldParameters jumpArgs;

	/**
	 * @return the keys
	 */
	public List<Key> getKeys()
	{
		return keys != null ? keys : Collections.<Key> emptyList();
	}
	
	public void addKey(Key key)
	{
		if(this.keys == null)
			this.keys = new ArrayList<Trigger.Key>();
		this.keys.add(key);
	}

	/**
	 * @return the fixedTimer
	 */
	public int getFixedTimer()
	{
		return fixedTimer;
	}

	/**
	 * @param fixedTimer the fixedTimer to set
	 */
	public void setFixedTimer(int fixedTimer)
	{
		if(fixedTimer != NO_TIMEOUT && fixedTimer < 0)
			throw new IllegalArgumentException("Invalid timer duration: " + fixedTimer);
		this.fixedTimer = fixedTimer;
	}

	/**
	 * @return the jump
	 */
	public Field getJump()
	{
		return jump;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.project.model.JumpSource#setJump(uk.ac.ucl.excites.sapelli.collector.project.model.Field)
	 */
	@Override
	public void setJump(Field target)
	{
		this.jump = target;
	}
	
}
