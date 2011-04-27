package apps.common;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JOptionPane;

import server.GameServer;

@SuppressWarnings("serial")
public class PublishButton extends JButton implements ActionListener {
    private GameServer theServer;
    
    public PublishButton(String theName) {
        super(theName);
        this.addActionListener(this);
        this.setEnabled(false);
    }
    
    public void setServer(GameServer theServer) {
        this.theServer = theServer;
        this.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this) {
            if (theServer != null) {
                if (theServer.getMatch().getGame().getRepositoryURL() != null) {
                    String theMatchKey = theServer.startPublishingToSpectatorServer("http://matches.ggp.org/");
                    String theURL = "http://matches.ggp.org/matches/" + theMatchKey + "/viz.html";
                    System.out.println("Publishing to: " + theURL);
                    int nChoice = JOptionPane.showConfirmDialog(this,
                            "Publishing successfully. Would you like to open the spectator view in a browser?",
                            "Publishing Match Online",
                            JOptionPane.YES_NO_OPTION);         
                    if (nChoice == JOptionPane.YES_OPTION) {                        
                        try {
                            java.awt.Desktop.getDesktop().browse(java.net.URI.create(theURL));
                        } catch (Exception ee) {
                            ee.printStackTrace();
                        }
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Could not publish a game that is only stored locally.",
                        "Publishing Match Online",
                        JOptionPane.ERROR_MESSAGE);
                }
                setEnabled(false);
            }
        }
    }
}