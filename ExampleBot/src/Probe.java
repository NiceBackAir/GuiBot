import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.PositionedObject;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.Chokepoint;

public class Probe extends MyUnit {

	public Probe(Unit u, Game game) {
		super(u, game);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return u.getPosition();
	}
	
	public void attack(Position pos, boolean attackBuildings) throws Exception {
		target = getTarget(attackBuildings);
		if(target != null) {
			if(isFree(target)) {
				if(!u.isUnderAttack() || u.getHitPoints() + u.getShields() > 32) {
					if(u.getGroundWeaponCooldown() == 0) {
						if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
							&& !u.getLastCommand().getTarget().equals(target) && u.isInWeaponRange(target)) && u.getGroundWeaponCooldown() == 0)
						
						u.attack(target);
					} else
						u.move(target.getPosition());
				} else {
					Unit closestPatch = getClosestPatch();						
					if(closestPatch != null)
						u.gather(closestPatch);
					else
						u.attack(target);
				}
				game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
			} else {
				game.drawTextMap(u.getPosition(), "busy");
			}
		} else if(isFree()) {
//			if((u.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Move
//			&& !u.getLastCommand().getTargetPosition().equals(pos))
//			|| u.getGroundWeaponCooldown() == 0)
//			
//			u.attack(pos);
//		if(u.getGroundWeaponCooldown() == 0) {
			if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move
				&& !u.getLastCommand().getTargetPosition().equals(pos)))
		
			u.attack(pos);
//		} else
//			u.move(pos);
			game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
		} else {
			game.drawTextMap(u.getPosition(), "busy");
		}
	}
}
