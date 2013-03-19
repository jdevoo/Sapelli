/**
 * 
 */
package uk.ac.ucl.excites.storage.model;

import java.io.IOException;
import java.text.ParseException;

import uk.ac.ucl.excites.storage.io.BitInputStream;
import uk.ac.ucl.excites.storage.io.BitOutputStream;
import uk.ac.ucl.excites.storage.types.Orientation;

/**
 * @author mstevens
 *
 */
public class OrientationColumn extends Column<Orientation>
{

	private boolean storeAzimuth;
	private boolean storePitch;
	private boolean storeRoll;
	
	/**
	 * 
	 */
	public OrientationColumn(String name, boolean optional, boolean storeAzimuth, boolean storePitch, boolean storeRoll)
	{
		super(name, optional);
		this.storeAzimuth = storeAzimuth;
		this.storePitch = storePitch;
		this.storeRoll = storeRoll;
	}

	@Override
	protected Orientation parse(String value) throws ParseException, IllegalArgumentException
	{
		return Orientation.Parse(value);
	}

	@Override
	protected String toString(Orientation value)
	{
		return value.toString();
	}

	@Override
	protected void write(Orientation value, BitOutputStream bitStream) throws IOException
	{
		if(storeAzimuth && value.hasAzimuth())
			bitStream.write(value.getAzimuth());
		if(storePitch && value.hasPitch())
			bitStream.write(value.getPitch());
		if(storeRoll && value.hasRoll())
			bitStream.write(value.getRoll());
	}

	@Override
	protected Orientation read(BitInputStream bitStream) throws IOException
	{
		Float azimuth = null, pitch = null, roll = null;
		if(storeAzimuth)
			azimuth = bitStream.readFloat();
		if(storePitch)
			pitch = bitStream.readFloat();
		if(storeRoll)
			roll = bitStream.readFloat();
		return new Orientation(azimuth, pitch, roll);
	}

	@Override
	protected void validate(Orientation value) throws IllegalArgumentException
	{
		//does nothing
	}

	@Override
	public boolean isVariableSize()
	{
		return false;
	}

	@Override
	public int getSize()
	{
		return (storeAzimuth ? Float.SIZE : 0) + (storePitch ? Float.SIZE : 0) + (storeRoll ? Float.SIZE : 0);
	}

}