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
}
