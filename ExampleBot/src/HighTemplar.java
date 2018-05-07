import java.util.HashMap;
import java.util.Map;

import bwapi.Game;
import bwapi.Position;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.Chokepoint;

public class HighTemplar extends MyUnit {

	public HighTemplar(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
	}

	public void attack(Position pos, boolean attackBuildings) throws Exception {
		if(!gotCommand) {
			techPosition = getTargetPosition();
			if(u.getEnergy() >= 75 && techPosition != null && u.getSpellCooldown() == 0) {
				u.useTech(TechType.Psionic_Storm, techPosition);
				gotCommand = true;

	//			game.sendText("storm");
	//			game.drawTextMap(u.getPosition(),""+u.getSpellCooldown());
			} else {
				if(u.getPosition().getApproxDistance(pos) < u.getType().sightRange()) {
					moveAwayFrom(pos);
				} else {
					move(pos);
					gotCommand = true;
				}
			}
		}
	}

	public Position getTargetPosition() throws Exception {
		HashMap<TilePosition, Double> stormMap = new HashMap<TilePosition, Double>();
		TilePosition tempTile = null;
		int playerFactor;
		double tileFactor;
		double cloakFactor;
		for (Unit unit: u.getUnitsInRadius(13*32)) {
			if(!unit.getType().isBuilding() && !unit.isUnderStorm() && unit.getType() != UnitType.Zerg_Larva
				&& unit.getType() != UnitType.Zerg_Egg) {
				
				playerFactor = 0;
				tileFactor = 1;
				cloakFactor = 1;
				
				if(unit.getPlayer() == game.enemy()) {
					playerFactor = 1;
					if(!unit.isDetected() && unit.isVisible()) {
						cloakFactor = 2;
					}
				} else if(unit.getPlayer() == game.self()) {
					playerFactor = -1;
				}
				
				for(int x = -1; x<=1; x++) {
					for(int y = -1; y<=1; y++) {
						if(x==0 && y== 0) {
							tileFactor = 1;
						} else {
							tileFactor = 0.8;
						}
						tempTile = new TilePosition(unit.getTilePosition().getX() +x, unit.getTilePosition().getY() + y);
						if(stormMap.get(tempTile) == null) {
							stormMap.put(tempTile, playerFactor*tileFactor*cloakFactor);
						} else {
							stormMap.replace(tempTile, stormMap.get(tempTile) + playerFactor*tileFactor*cloakFactor);
						}
					}
				}

			}
		}
		
		//find the best storm spot
		TilePosition stormLocation = null;
		double mostCasualties = 0;
		for(Map.Entry<TilePosition, Double> entry: stormMap.entrySet()) {
			//set storm minimum here
			if(entry.getValue() >= 3.5 && entry.getValue() > mostCasualties) {
				mostCasualties = entry.getValue();
				stormLocation = entry.getKey();
			}
		}
		if(stormLocation != null)
			return new Position(stormLocation.getX()*32 + 16, stormLocation.getY()*32 + 16);
		else
			return null;
	}
}
