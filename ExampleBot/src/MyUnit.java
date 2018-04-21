import bwapi.Game;
import bwapi.Position;
import bwapi.PositionedObject;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.Chokepoint;

public class MyUnit extends PositionedObject {
	protected Unit u;
	protected Position objective;
	protected Position attackPoint;
	protected Unit target;
	protected Position retreatPoint;
	protected int scale = 5*32;
	protected UnitState state;
	protected Squad squad;
	protected boolean gotCommand;
	protected Game game;
	
	public static MyUnit createFrom(Unit u, Game game) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		MyUnit unit = new MyUnit(u, game);
		return unit;
	}
	
	public MyUnit(Unit u, Game game) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		this.game = game;
		this.u = u;
	}

	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return u.getPosition();
	}
	
	public void command(UnitState myCommand, Position pos) {		
	}
	public void command(UnitState myCommand, Chokepoint choke, int range) throws Exception {		
		if(myCommand == UnitState.HOLDING) {
			blockChoke(choke);
		}
	}
	
	public void moveDownGradient() {
		
	}
	
	public void scout() {
	}
	
	public boolean[] attack(Position pos, boolean attackBuildings) throws Exception {
		if(isFree(attackBuildings)) {
			if(u.getGroundWeaponCooldown() == 0)
				u.attack(pos);
			else
				u.move(pos);
		} else {
			game.drawTextMap(u.getPosition(), "busy");
		}
		return null;
	}
//	public boolean[] attack(Unit hisUnit) {
//		if(isFree(hisUnit)) {
//			u.attack(hisUnit);
//		}
//		return null;
//	}
	public boolean[] holdPosition() throws Exception {
		if(isFree(true)) {
			u.holdPosition();
		}
		return null;
	}
	public boolean[] move(Position pos) throws Exception {
		if(isFree(true)) {
			u.move(pos);
		}
		return null;
	}
	
	public void blockChoke(Chokepoint choke) throws Exception {
		if(choke != null && isFree(true)) {
			if(GuiBot.intersects(u.getType(), u.getPosition(), choke.getSides().first, choke.getSides().second)) {
				if(!u.isHoldingPosition())
					u.holdPosition();
			} else if(choke.getCenter().getApproxDistance(u.getPosition()) < choke.getWidth()/1.5) {
				if(!u.isHoldingPosition()) {
					if(u.isStuck()) {// && GuiBot.walkMap[u.getTilePosition().getX()][u.getTilePosition().getY()] >= 0) {
						u.holdPosition();
					} else if(BWTA.getRegion(u.getPosition()).equals(choke.getRegions().first)) {				
						u.attack(choke.getRegions().second.getCenter());
					} else {
						u.attack(choke.getRegions().first.getCenter());
					}
				}
			} else
				u.move(choke.getCenter());
		}
	}
	
	//default check to see if unit's attack will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree(boolean attackBuildings) throws Exception {
		if(u.isLoaded())
			return false;
		if(u.getGroundWeaponCooldown() < u.getType().groundWeapon().damageCooldown()-1 && u.getGroundWeaponCooldown() > 0)
			return true;	
		
		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move				
			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Hold_Position) {
			
			for(Unit hisUnit: u.getUnitsInRadius(u.getType().seekRange())) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
				if(hisUnit.getPlayer() == game.enemy()) {
//					game.sendText("hi");
//					game.drawTextMap(u.getPosition(), ""+u.getLastCommand().getUnitCommandType());
					if(u.isInWeaponRange(hisUnit) && hisUnit.isDetected() && !hisUnit.isInvincible() && u.canAttack(hisUnit)
						&& (attackBuildings || !hisUnit.getType().isBuilding() || !hisUnit.getType().groundWeapon().equals(WeaponType.None)
						|| hisUnit.getType() == UnitType.Terran_Bunker)					
						&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
						
						return false;
					}
				}
			}
			return true;
		}
		return true;
	}
	//default check to see if unit's attack unit command will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree(Unit hisUnit) {
		if(u.isLoaded())
			return false;
		if(u.getGroundWeaponCooldown() < u.getType().groundWeapon().damageCooldown() && u.getGroundWeaponCooldown() > 0)
			return true;
		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit && u.getLastCommand().getTarget().equals(hisUnit)
			&& hisUnit.isDetected() && !hisUnit.isInvincible() && hisUnit.getType() != UnitType.Resource_Vespene_Geyser 
			&& hisUnit.exists() && u.isInWeaponRange(hisUnit) && u.canAttack(hisUnit) && u.isInWeaponRange(hisUnit)) {
			
			return false;
		}
		return true;
	}
	
	//respond to a bulk army attack command
	public double[] attack() {
		return null;
	}
	
	//respond to a bulk army retreat command
	public double[] retreat() {
		return null;
	}
	
	//respond to a bulk refend position command
	public double[] defend(Position rallyPoint) {
		return null;
	}
	
	public double[] adjustForCollision() {
		return null;
	}
	
	public double[] dyingWishes() {
		return null;
	}
	
	public void driveShuttle(MyUnit shuttle) {
		
	}
	
	
	public Unit getUnit() {
		return u;
	}
	public Squad getSquad() {
		return squad;
	}
}
