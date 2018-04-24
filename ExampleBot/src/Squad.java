import java.util.ArrayList;
import java.util.Iterator;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class Squad {
	private ArrayList<MyUnit> units;
	private UnitState command;
	private Position objective;
	private int radius;
	private BaseLocation base;
	private Game game;
	private boolean seesEnemies;
	private Position center;
	
	public Squad(Game game) {
		units = new ArrayList<MyUnit>();
		this.game = game;
	}
	public Squad(ArrayList<MyUnit> myUnits, Game game) {
		units = myUnits;
		this.game = game;
	}
	
	public void add(MyUnit myUnit) {
		units.add(myUnit);		
	}
	
	public void command(UnitState myCommand, Position pos, int range) {
		command = myCommand;
		objective = pos;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.command(myCommand, pos);
			} else {
				itr.remove();
			}
		}
	}
	public void holdChoke(Chokepoint choke) throws Exception {
		command = UnitState.CONTAINING;
		objective = choke.getCenter();
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.blockChoke(choke);
			} else {
				itr.remove();
			}
		}
		center = findCenter();
	}
	public void contain(Position pos, int range) throws Exception {
		objective = pos;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		double d;
		center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				d = myUnit.getPosition().getApproxDistance(pos);
				if(d >= range + 5*32) {
					myUnit.move(pos);
				} else if(d >= range)
					myUnit.surround(pos, center, range);
				else
					myUnit.moveAwayFrom(pos);
			} else {
				itr.remove();
			}
		}
	}
	public void attack(Position attackPosition, int range) throws Exception {
		// TODO Auto-generated method stub
		command = UnitState.ATTACKING;
		objective = attackPosition;
		radius = range;
		center = findCenter();
		boolean attackBuildings = true;
		for(Unit hisUnit: game.getUnitsInRadius(attackPosition, range)) {
			if(hisUnit.getPlayer() == game.enemy() && hisUnit.isDetected() && !hisUnit.isInvincible()
				&& (!hisUnit.getType().isBuilding() || hisUnit.getType().canAttack()
				|| hisUnit.getType() == UnitType.Terran_Bunker) && hisUnit.getType() != UnitType.Resource_Vespene_Geyser					
				&& hisUnit.getType() != UnitType.Zerg_Egg && hisUnit.getType() != UnitType.Zerg_Larva ) {
				
				attackBuildings = false;
				break;
			}				
		}
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		if(isStaged(attackPosition, range)) {
			while(itr.hasNext()) {
				myUnit = itr.next();
				if(myUnit.getUnit().exists()) {
					myUnit.attack(attackPosition, attackBuildings);
				} else {
					itr.remove();
				}
			}
		} else {
			if(isTogether() || attackPosition.getApproxDistance(center) > 40*32 || attackPosition.getApproxDistance(center) < 15*32) {
				contain(attackPosition, range);
			} else {
				groupUp(center);
			}			
		}
		game.drawCircleMap(attackPosition,range, Color.Red);
	}
	public Position findCenter() {
		int centerX = 0;
		int centerY = 0;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		int unitCount = 0;
		while(itr.hasNext()) {
			myUnit = itr.next();
			centerX += myUnit.getX();
			centerY += myUnit.getY();
			unitCount++;
		}
		if(unitCount == 0) 
			return null;
		
		centerX /= unitCount;
		centerY /= unitCount;

		game.drawCircleMap(new Position(centerX, centerY), 3, Color.Green);
		game.drawTextMap(new Position(centerX, centerY), ""+unitCount);
		return new Position(centerX, centerY);		
	}
	public void groupUp(Position pos) throws Exception {
		objective = pos;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		Position center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			myUnit.move(center);
		}
	}
	public ArrayList<MyUnit> getUnits() {
		return units;
	}
	public UnitState getCommand() {
		return command;
	}
	public Position getObjective() {
		return objective;
	}
	public void takeAllUnits(Squad squad) {
		units.addAll(squad.getUnits());
		squad.clearUnits();
	}
	public void clearUnits() {
		units.clear();
	}
	public boolean isTogether() {
		Position center = findCenter();
		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(center) >= 10*32) {
				return false;
			}
		}
		return true;
	}
	public boolean isStaged(Position pos, int range) {		
		Position center = findCenter();
		if(center != null && center.getApproxDistance(pos) <  range-32)
			return true;

		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(pos) >= range + 3*32) {
				return false;
			}
		}		
		return true;
	}
}
