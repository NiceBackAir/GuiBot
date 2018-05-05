import bwapi.Color;
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
	protected int scale;
	protected UnitState state;
	protected Squad squad;
	protected boolean gotCommand;
	protected Game game;
	protected int cancelFrames;
	protected Position techPosition;
	
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
		scale = 5*32;
		techPosition = null;
		//stupid goon cancel frames
		cancelFrames = 0;
		gotCommand = false;
		squad = null;
	}

	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return u.getPosition();
	}
	
	public Unit getClosestPatch() {
		Unit closestPatch = null;
		for(Unit neutralUnit: u.getUnitsInRadius(u.getType().sightRange())) {
			if(neutralUnit.getType().isMineralField() && (closestPatch == null 
				|| u.getPosition().getApproxDistance(neutralUnit.getPosition()) 
				< u.getPosition().getApproxDistance(closestPatch.getPosition()))) {
				
				closestPatch = neutralUnit;
			}
		}
		return closestPatch;
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
	
	public void attack(Position pos, boolean attackBuildings) throws Exception {
		target = getTarget(attackBuildings);
//		if(u.isStuck())
//			game.sendText("stuck");	
		if(target != null) {
			if(isFree(target)) {
				if(u.getGroundWeaponCooldown() == 0) {
					if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Unit
						&& !u.getLastCommand().getTarget().equals(target) && u.isInWeaponRange(target)) && u.getGroundWeaponCooldown() == 0)
					
					u.attack(target);
				} else
					u.move(target.getPosition());
//				game.drawTextMasp(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
			} else {
//				game.drawTextMap(u.getPosition(), "busy");
			}
		} else if(isFree()) {
//			if((u.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Move
//				&& !u.getLastCommand().getTargetPosition().equals(pos))
//				|| u.getGroundWeaponCooldown() == 0)
//				
//				u.attack(pos);
//			if(u.getGroundWeaponCooldown() == 0) {
				if(!(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move
					&& !u.getLastCommand().getTargetPosition().equals(pos)))
			
				u.attack(pos);
//			} else
//				u.move(pos);
//			game.drawTextMap(u.getPosition(),""+u.getLastCommand().getUnitCommandType());
		} else {
//			game.drawTextMap(u.getPosition(), "busy");
		}
	}
//	public boolean[] attack(Unit hisUnit) {
//		if(isFree(hisUnit)) {
//			u.attack(hisUnit);
//		}
//		return null;
//	}
	public boolean[] holdPosition() throws Exception {
		target = getTarget(false);
		if(isFree() && !u.isHoldingPosition()) {
			u.holdPosition();
		}
		return null;
	}
	public boolean[] move(Position pos) throws Exception {
		target = null;
		if(!gotCommand)
			u.move(pos);
		
		gotCommand = true;
		return null;
	}
	public void moveAwayFrom(Position pos) throws Exception {
		double d = u.getPosition().getApproxDistance(pos);
		double[] moveVector = {(u.getX()-pos.getX())*scale/d, (u.getY()-pos.getY())*scale/d};
		moveVector = terrainCorrection(moveVector);
 		
 		u.move(new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]));
// 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)moveVector[0], u.getY() + (int)moveVector[1]), Color.White);
	}
	
	/** Surround enemies like water, literally
	 * 
	 */
	public void surround(Position pos, Position origin, int R) {
		double d = pos.getApproxDistance(origin);
		double[] flowVector = {(pos.getX()-origin.getX())/d, (pos.getY()-origin.getY())/d};
		double[] moveVector = new double[2];
		double r = u.getPosition().getApproxDistance(pos);
		double[] rVector = {(pos.getX()-u.getX())/r, (pos.getY()-u.getY())/r};
		double cosTheta = flowVector[0]*rVector[0] + flowVector[1]*rVector[1];
		double sinTheta = flowVector[0]*rVector[1] - flowVector[1]*rVector[0];
		double cosFlowAngle = flowVector[0];
		double sinFlowAngle = flowVector[1];
		
		//V_r
		moveVector[0] += (1 - R*R/r/r)*cosTheta*(cosTheta*cosFlowAngle - sinTheta*sinFlowAngle);
		moveVector[1] += (1 - R*R/r/r)*cosTheta*(sinTheta*cosFlowAngle + cosTheta*sinFlowAngle);
		//V_theta
		moveVector[0] += -1*(1 + R*R/r/r)*sinTheta*-1*(sinTheta*cosFlowAngle + cosTheta*sinFlowAngle);
		moveVector[1] += -1*(1 + R*R/r/r)*sinTheta*(cosTheta*cosFlowAngle - sinTheta*sinFlowAngle);		
				
		moveVector = GuiBot.setMagnitude(moveVector, scale);
		moveVector = terrainCorrection(moveVector);
// 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)moveVector[0], u.getY() + (int)moveVector[1]), Color.Blue);
		
 		u.move(new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]));
	}
	public double[] terrainCorrection(double[] moveVector) {
		double d;
		double[] terrainVector = new double[2];
 		//don't walk into walls
 		int tileX = u.getTilePosition().getX();
 		int tileY = u.getTilePosition().getY();
 		int xPos;
 		int yPos;
 		if(GuiBot.walkMap[tileX][tileY] == 0) {
 			for(int x=tileX-1; x<= tileX+1; x++) {
 				for(int y=tileY-1;y<=tileY+1;y++) {
 					if(x>=0 && x<game.mapWidth() && y>=0 && y<=game.mapHeight()) {
     					xPos = x*32 + 16;
     					yPos = y*32 + 16;
     					d = u.getPosition().getApproxDistance(new Position(xPos, yPos));
     					terrainVector[0] += -(GuiBot.walkMap[x][y]-GuiBot.walkMap[tileX][tileY])*(xPos-u.getX())/d;
     					terrainVector[1] += -(GuiBot.walkMap[x][y]-GuiBot.walkMap[tileX][tileY])*(yPos-u.getY())/d;
 					}
 				}
 			}
 		}
 		terrainVector = GuiBot.setMagnitude(terrainVector, 1);
 		terrainVector = GuiBot.setMagnitude(terrainVector, -terrainVector[0]*moveVector[0]-terrainVector[1]*moveVector[1]);

 		moveVector[0] += terrainVector[0];
 		moveVector[1] += terrainVector[1];
 		return moveVector;
	}
	
	public void blockChoke(Chokepoint choke) throws Exception {
		target = getTarget(false);
//		if(u.isStuck()) 
//			game.sendText("stuck");	
		if(choke != null && isFree()) {
			if(u.getType().groundWeapon() != WeaponType.None && game.self().weaponMaxRange(u.getType().groundWeapon()) <= 3*32
				&& GuiBot.intersects(u.getType(), u.getPosition(), choke.getSides().first, choke.getSides().second)) {
				//melee units can wall
				if(!u.isHoldingPosition())
					u.holdPosition();
			} else if(choke.getCenter().getApproxDistance(u.getPosition()) < choke.getWidth()/1.5) {
				if(!u.isHoldingPosition()) {
					if(BWTA.getRegion(u.getPosition()).equals(choke.getRegions().first)) {	
						attack(choke.getRegions().second.getCenter(), false);
					} else {
						attack(choke.getRegions().first.getCenter(), false);
					}
				}
			} else
				attack(choke.getCenter(), false);
		}
	}
		
	public Unit getTarget(boolean attackBuildings) throws Exception {
		Unit closestEnemy = null;
		int range = Math.max(u.getType().seekRange(), game.self().weaponMaxRange(u.getType().groundWeapon()));
		if(range == 0 || !u.getType().canAttack()) {
			range = u.getType().sightRange();
		}
//		System.out.println(range);
		for(Unit hisUnit: u.getUnitsInRadius(range)) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
			if(hisUnit.getPlayer() == game.enemy()) {
				if(u.isInWeaponRange(hisUnit) && hisUnit.isDetected() && !hisUnit.isInvincible()// && u.canAttack(hisUnit, true, false, false)
					&& (attackBuildings || !hisUnit.getType().isBuilding() || hisUnit.getType().canAttack()
					|| hisUnit.getType() == UnitType.Terran_Bunker)					
					&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
					
					if(closestEnemy == null || hisUnit.getDistance(u) < closestEnemy.getDistance(u)) {
						closestEnemy = hisUnit;
					}
				}
			}
		}
//		if(closestEnemy != null)
//			game.drawLineMap(u.getPosition(), closestEnemy.getPosition(), Color.White);
		return closestEnemy;
	}
	//default check to see if unit's attack will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree() throws Exception {
		if(gotCommand)
			return false;
		if(u.isLoaded())
			return false;	
		if(u.getGroundWeaponCooldown() < u.getType().groundWeapon().damageCooldown()-cancelFrames-1 && u.getGroundWeaponCooldown() > 0)
			return true;
		
		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move				
			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Hold_Position
			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Patrol) {
			
			if(target != null)
				return false;
		}
			
		return true;
	}
	
	//default check to see if unit's attack unit command will not be interrupted with another command. Good for zealots, DTs, and Archons
	public boolean isFree(Unit hisUnit) {
		if(gotCommand)
			return false;
		if(u.isLoaded())
			return false;
		if(u.getGroundWeaponCooldown() < u.getType().groundWeapon().damageCooldown()-cancelFrames-1 && u.getGroundWeaponCooldown() > 0)
			return true;
		if(u.getLastCommand().getTarget() != null && u.getLastCommand().getTarget().equals(hisUnit)
			&& hisUnit.isDetected() && hisUnit.getType() != UnitType.Resource_Vespene_Geyser 
			&& hisUnit.exists() && u.isInWeaponRange(hisUnit)) {// && u.canAttack(hisUnit, true, false, false)) {
			
			return false;
		}
		return true;
	}
	
	//respond to a bulk army retreat command
	public double[] retreat() {
		return null;
	}
	
	//respond to a bulk defend position command
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
	public boolean hasCommand() {
		return gotCommand;
	}
	public void setSquad(Squad squad) {
		this.squad = squad;
	}
	public void setCommandGiven(boolean commanded) {
		gotCommand = commanded;
	}
}
