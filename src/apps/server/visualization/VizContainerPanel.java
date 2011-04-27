package apps.server.visualization;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import util.xhtml.GameStateRenderPanel;

@SuppressWarnings("serial")
public class VizContainerPanel extends JPanel {	
	public VizContainerPanel(String XML, String XSL, boolean isLocal, VisualizationPanel parent) 
	{
		Dimension d = GameStateRenderPanel.getDefaultSize();
		setPreferredSize(d);
		
		BufferedImage backimage = parent.getGraphicsConfiguration().createCompatibleImage(d.width, d.height);
		GameStateRenderPanel.renderImagefromGameXML(XML, XSL, isLocal, backimage);
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(backimage, "png", bos);
			compressed = bos.toByteArray();
			imageWritten = true;
		} catch (Exception ex) {
		    ex.printStackTrace();
		}
	}
	
	private byte[] compressed = null;
	private boolean imageWritten = false;
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (imageWritten) {
			try {
				BufferedImage img2;
				img2 = ImageIO.read(new ByteArrayInputStream(compressed));
				g.drawImage(img2, 0, 0, null);
			} catch (Exception ex) {
			    ex.printStackTrace();
			}
       }
	}
}