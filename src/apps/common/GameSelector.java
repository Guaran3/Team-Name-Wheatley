package apps.common;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;

import util.game.CloudGameRepository;
import util.game.Game;
import util.game.GameRepository;
import util.game.LocalGameRepository;

/**
 * GameSelector is a pair of widgets for selecting a game repository
 * and then choosing a game from that game repository. Currently this
 * is a little rough, and could use some polish, but it provides all
 * of the important functionality: you can load games both from local
 * storage and from game repositories on the web.
 * 
 * @author Sam Schreiber
 */
public class GameSelector implements ActionListener {
    JComboBox theGameList;
    JComboBox theRepositoryList;
    
    GameRepository theSelectedRepository;
    Map<String, GameRepository> theCachedRepositories;

    class NamedItem {
        public final String theKey;
        public final String theName;
        
        public NamedItem(String theKey, String theName) {
            this.theKey = theKey;
            this.theName = theName;
        }
        
        public String toString() {
            return theName;
        }
    }
    
    public GameSelector() {
        theGameList = new JComboBox();
        theGameList.addActionListener(this); 
        
        theRepositoryList = new JComboBox();
        theRepositoryList.addActionListener(this);

        theCachedRepositories = new HashMap<String, GameRepository>();        
        theRepositoryList.addItem("games.ggp.org");
        theRepositoryList.addItem("Local Game Repository");
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == theRepositoryList) {
            String theRepositoryName = theRepositoryList.getSelectedItem().toString();
            if (theCachedRepositories.containsKey(theRepositoryName)) {
                theSelectedRepository = theCachedRepositories.get(theRepositoryName);
            } else {
                if (theRepositoryName.equals("Local Game Repository")) {
                    theSelectedRepository = new LocalGameRepository();
                } else {
                    theSelectedRepository = new CloudGameRepository(theRepositoryName);
                }
                theCachedRepositories.put(theRepositoryName, theSelectedRepository);
            }
            repopulateGameList();
        }
    }    
    
    public GameRepository getSelectedGameRepository() {
        return theSelectedRepository;
    }
    
    public void repopulateGameList() {        
        GameRepository theRepository = getSelectedGameRepository();        
        List<String> theKeyList = new ArrayList<String>(theRepository.getGameKeys());
        Collections.sort(theKeyList);
        theGameList.removeAllItems();
        for (String theKey : theKeyList) {
            String theName = theRepository.getGame(theKey).getName();
            if (theName == null) {
                theName = theKey;
            }
            if (theName.length() > 24)
                theName = theName.substring(0, 24) + "...";
            theGameList.addItem(new NamedItem(theKey, theName));
        }
    }
    
    public JComboBox getRepositoryList() {
        return theRepositoryList;
    }
    
    public JComboBox getGameList() {
        return theGameList;
    }
    
    public Game getSelectedGame() {
        try {
            return getSelectedGameRepository().getGame(((NamedItem)theGameList.getSelectedItem()).theKey);
        } catch(Exception e) {
            return null;
        }
    }
}