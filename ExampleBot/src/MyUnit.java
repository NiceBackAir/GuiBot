import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.PositionedObject;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
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
	
	public void attack(Position pos, boolean attackBuildings) throws Exception {
		if(isFree(attackBuildings)) {
//			if(u.getGroundWeaponCooldown() == 0)
			if((u.getLastCommand().getUnitCommandType() != UnitCommandType.Attack_Move
				&& !u.getLastCommand().getTargetPosition().equals(pos))
				|| u.getGroundWeaponCooldown() == 0)
				
				u.attack(pos);
//			else
//				u.move(pos);
		} else {
			game.drawTextMap(u.getPosition(), "busy");
		}
	}
//	public boolean[] attack(Unit hisUnit) {
//		if(isFree(hisUnit)) {
//			u.attack(hisUnit);
//		}
//		return null;
//	}
	public boolean[] holdPosition() throws Exception {
		if(isFree(true) && !u.isHoldingPosition()) {
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
	public void moveAwayFrom(Position pos) throws Exception {
		double d = u.getPosition().getApproxDistance(pos);
		double[] moveVector = {(u.getX()-pos.getX())*scale/d, (u.getY()-pos.getY())*scale/d};
		moveVector = terrainCorrection(moveVector);
 		
 		u.move(new Position(u.getX()+(int)moveVector[0], u.getY()+(int)moveVector[1]));
 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)moveVector[0], u.getY() + (int)moveVector[1]), Color.White);
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
 		game.drawLineMap(u.getPosition(), new Position(u.getX() + (int)moveVector[0], u.getY() + (int)moveVector[1]), Color.Blue);
		moveVector = terrainCorrection(moveVector);
		
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
		
//		if(u.getLastCommand().getUnitCommandType() == UnitCommandType.Attack_Move				
//			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Hold_Position
//			|| u.getLastCommand().getUnitCommandType() == UnitCommandType.Move) {
			
			for(Unit hisUnit: u.getUnitsInRadius(u.getType().seekRange())) {// u.getUnitsInWeaponRange(u.getType().groundWeapon())) {
				if(hisUnit.getPlayer() == game.enemy()) {
//					game.sendText("hi");
//					game.drawTextMap(u.getPosition(), ""+u.getLastCommand().getUnitCommandType());
					if(u.isInWeaponRange(hisUnit) && hisUnit.isDetected() && !hisUnit.isInvincible() && u.canAttack(hisUnit)
						&& (attackBuildings || !hisUnit.getType().isBuilding() || !hisUnit.getType().canAttack()
						|| hisUnit.getType() == UnitType.Terran_Bunker)					
						&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
						
						return false;
					}
				}
			}
//			return true;
//		}
			
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
