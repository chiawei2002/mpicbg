import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.gui.*;

import mpicbg.models.*;

import java.awt.Event;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class InteractiveTransform implements PlugIn, MouseListener,  MouseMotionListener, KeyListener, ImageListener
{
	protected ImagePlus imp;
	protected ImageProcessor target;
	protected ImageProcessor source;
	final protected AtomicBoolean pleaseRepaint = new AtomicBoolean( false );
	
	protected Point[] p;
	protected Point[] q;
	final ArrayList< PointMatch > m = new ArrayList< PointMatch >();
	PointRoi handles;
	
	protected PaintInvertibleCoordinateTransformThread painter;
	
	int targetIndex = -1;
	
	abstract protected InvertibleModel myModel();
	abstract protected void setHandles();
	abstract protected void updateHandles( int x, int y );
	
	public void run( String arg )
    {
		// cleanup
		m.clear();
		
		imp = IJ.getImage();
		target = imp.getProcessor();
		source = target.duplicate();
		
		painter = new PaintInvertibleCoordinateTransformThread(
				imp,
				source,
				target,
				pleaseRepaint,
				myModel() );
		painter.start();
		
		setHandles();
		
		Toolbar.getInstance().setTool( Toolbar.getInstance().addTool( "Drag_the_handles." ) );
		
		imp.getCanvas().addMouseListener( this );
		imp.getCanvas().addMouseMotionListener( this );
		imp.getCanvas().addKeyListener( this );
    }
	
	public void imageClosed( ImagePlus imp )
	{
		if ( imp == this.imp )
			painter.interrupt();
	}
	public void imageOpened( ImagePlus imp ){}
	public void imageUpdated( ImagePlus imp ){}
	
	public void keyPressed( KeyEvent e)
	{
		if ( e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER )
		{
			painter.interrupt();
			if ( imp != null )
			{
				imp.getCanvas().removeMouseListener( this );
				imp.getCanvas().removeMouseMotionListener( this );
				imp.getCanvas().removeKeyListener( this );
				imp.getCanvas().setDisplayList( null );
				imp.setRoi( ( Roi )null );
			}
			if ( e.getKeyCode() == KeyEvent.VK_ESCAPE )
			{
				imp.setProcessor( null, source );
			}
		}
		else if (
				( e.getKeyCode() == KeyEvent.VK_F1 ) &&
				( e.getSource() instanceof TextField ) ){}
	}

	public void keyReleased( KeyEvent e ){}
	public void keyTyped( KeyEvent e ){}
	
	public void mousePressed( MouseEvent e )
	{
		targetIndex = -1;
		if ( e.getButton() == MouseEvent.BUTTON1 )
		{
			ImageWindow win = WindowManager.getCurrentWindow();
			int x = win.getCanvas().offScreenX( e.getX() );
			int y = win.getCanvas().offScreenY( e.getY() );
			
			double target_d = Double.MAX_VALUE;
			for ( int i = 0; i < q.length; ++i )
			{
				double dx = win.getCanvas().getMagnification() * ( q[ i ].getW()[ 0 ] - x );
				double dy = win.getCanvas().getMagnification() * ( q[ i ].getW()[ 1 ]  - y );
				double d =  dx * dx + dy * dy;
				if ( d < 64.0 && d < target_d )
				{
					targetIndex = i;
					target_d = d;
				}
			}
		}
	}

	public void mouseReleased( MouseEvent e ){}
	public void mouseExited( MouseEvent e ) {}
	public void mouseClicked( MouseEvent e ) {}	
	public void mouseEntered( MouseEvent e ) {}
	
	public void mouseDragged( MouseEvent e )
	{
		if ( targetIndex >= 0 )
		{
			ImageWindow win = WindowManager.getCurrentWindow();
			int x = win.getCanvas().offScreenX( e.getX() );
			int y = win.getCanvas().offScreenY( e.getY() );
			
			updateHandles( x, y );
					
			try
			{
				myModel().fit( m );
				synchronized ( painter )
				{
					if ( pleaseRepaint.compareAndSet( false, true ) )
						painter.notify();
				}
			}
			catch ( NotEnoughDataPointsException ex ) { ex.printStackTrace(); }
		}
	}
	
	public void mouseMoved( MouseEvent e ){}
	
	
	public static String modifiers( int flags )
	{
		String s = " [ ";
		if ( flags == 0 )
			return "";
		if ( ( flags & Event.SHIFT_MASK ) != 0 )
			s += "Shift ";
		if ( ( flags & Event.CTRL_MASK ) != 0 )
			s += "Control ";
		if ( ( flags & Event.META_MASK ) != 0 )
			s += "Meta (right button) ";
		if ( ( flags & Event.ALT_MASK ) != 0 )
			s += "Alt ";
		s += "]";
		if ( s.equals( " [ ]" ) )
			s = " [no modifiers]";
		return s;
	}
}