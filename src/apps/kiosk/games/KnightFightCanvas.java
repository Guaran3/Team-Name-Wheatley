package apps.kiosk.games;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Set;

import apps.kiosk.templates.CommonGraphics;
import apps.kiosk.templates.GameCanvas_Chessboard;

public class KnightFightCanvas extends GameCanvas_Chessboard {
    private static final long serialVersionUID = 1L;

    public String getGameName() { return "Knight Fight"; }
    protected String getGameKey() { return "knightfight"; }
    protected int getGridHeight() { return 10; }
    protected int getGridWidth() { return 10; }    

    @Override
    protected Set<String> getFactsAboutCell(int xCell, int yCell) {
        return gameStateHasFactsMatching("\\( cell " + xCell + " " + yCell + " (.*) \\)");
    }
    
    @Override
    protected Set<String> getLegalMovesForCell(int xCell, int yCell) {
        Set<String> theMoves = gameStateHasLegalMovesMatching("\\( move " + xCell + " " + yCell + " (.*) \\)");
        theMoves.addAll(gameStateHasLegalMovesMatching("ortho"));
        theMoves.addAll(gameStateHasLegalMovesMatching("diag"));
        return theMoves;
    }

    protected void renderCellContent(Graphics g, String theFact) {
        String[] cellFacts = theFact.split(" ");
        String cellType = cellFacts[4];
        if(cellType.equals("b")) return;
        
        if(cellType.equals("arrow")) {
            CommonGraphics.drawBubbles(g, theFact.hashCode());
        } else {
            CommonGraphics.drawChessPiece(g, cellType.charAt(0) + "n");
        }
    }
    
    @Override
    protected void renderMoveSelectionForCell(Graphics g, int xCell, int yCell, String theMove) {
        int width = g.getClipBounds().width;
        int height = g.getClipBounds().height;        
        
        String[] moveParts = theMove.split(" ");
        
        if(moveParts[0].equals("ortho")) {
            if(isSelectedCell(xCell, yCell))
                CommonGraphics.fillWithString(g, "+", 1.2);
        } else if(moveParts[0].equals("diag")) {
            if(isSelectedCell(xCell, yCell))
                CommonGraphics.fillWithString(g, "x", 1.2);
        } else {        
            int xTarget = Integer.parseInt(moveParts[4]);
            int yTarget = Integer.parseInt(moveParts[5]);
            if(xCell == xTarget && yCell == yTarget) {
                g.setColor(new Color(0, 0, 255, 192));                
                g.drawRect(3, 3, width-6, height-6);
                CommonGraphics.fillWithString(g, "X", 3);
            }
        }
    }
}