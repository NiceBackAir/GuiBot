import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitCommandType;
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
	private HashMap<UnitType, Integer> unitTab;
	
	public Squad(Game game) {
		units = new ArrayList<MyUnit>();
		this.game = game;
		unitTab = new HashMap<UnitType, Integer>();
	}
	public Squad(ArrayList<MyUnit> myUnits, Game game) {
		units = myUnits;
		this.game = game;
		unitTab = new HashMap<UnitType, Integer>();
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
		command = UnitState.HOLDING;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				myUnit.blockChoke(choke);
//				game.drawCircleMap(myUnit.getPosition(), 16, Color.Red);
			} else {
				itr.remove();
			}
		}
	}
	public void attack(Position attackPosition, int range) throws Exception {
		objective = attackPosition;
		attack(range);
	}
	public void attack(int range) throws Exception {
		// TODO Auto-generated method stub
		command = UnitState.ATTACKING;
		radius = range;
		center = findCenter();
		boolean attackBuildings = true;
		for(Unit hisUnit: game.getUnitsInRadius(objective, range)) {
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
		if(isStaged(objective, range)) {
			while(itr.hasNext()) {
				myUnit = itr.next();
				if(myUnit.getUnit().exists()) {
					myUnit.attack(objective, attackBuildings);
					game.drawCircleMap(myUnit.getPosition(), 16, Color.Green);
				} else {
					itr.remove();
				}
			}
		} else {
			if(isTogether() || objective.getApproxDistance(center) < 15*32) {
				contain(objective, range);
			} else {
				groupUp(center);
			}			
		}
//		game.drawCircleMap(objective,range, Color.Red);
//		game.drawLineMap(objective, center, Color.Green);
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
				
				game.drawCircleMap(myUnit.getPosition(), 16, Color.Blue);
			} else {
				itr.remove();
			}
		}
	}
	public void resumeMining() {
		command = UnitState.MINING;
		UnitCommandType command = null;
		for(MyUnit myUnit: units) {
			command = myUnit.getUnit().getLastCommand().getUnitCommandType();
			if(command != UnitCommandType.Stop && (command == UnitCommandType.Attack_Move || command == UnitCommandType.Attack_Unit 
			|| command == UnitCommandType.Patrol || command == UnitCommandType.Hold_Position))
				myUnit.getUnit().stop();
		}
	}
	public void groupUp(Position pos) throws Exception {
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		Position center = findCenter();
		while(itr.hasNext()) {
			myUnit = itr.next();
			if(myUnit.getUnit().exists()) {
				if(myUnit.getPosition().getApproxDistance(center) >= 10*32-16
					&& GuiBot.walkMap[center.toTilePosition().getX()][center.toTilePosition().getY()] <= 0) {
					myUnit.move(center);
				}
//				game.drawCircleMap(myUnit.getPosition(),16, Color.White);
			} else {
				itr.remove();
			}
		}
	}
	public Position findCenter() {
		unitTab = new HashMap<UnitType, Integer>();
		UnitType type = null;
		
		int centerX = 0;
		int centerY = 0;
		Iterator<MyUnit> itr = units.iterator();
		MyUnit myUnit;
		int units = 0;
		while(itr.hasNext()) {
			myUnit = itr.next();
			centerX += myUnit.getX();
			centerY += myUnit.getY();
			units++;
			type = myUnit.getUnit().getType();
			if(unitTab.containsKey(type)) {
				unitTab.put(type, unitTab.get(type) + 1);
			} else {
				unitTab.put(type, 1);
			}
		}
		if(units == 0) 
			return null;
		
		centerX /= units;
		centerY /= units;

		center = new Position(centerX, centerY);
//		game.drawCircleMap(new Position(centerX, centerY), 3, Color.Green);
//		game.drawTextMap(new Position(centerX, centerY), ""+units);
		
		return center;		
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
	public void setObjective(Position pos) {
		objective = pos;
	}
	public void takeUnit(MyUnit u, Iterator<MyUnit> itr) {
		units.add(u);
		itr.remove();
	}
	public void takeAllUnits(Squad squad) {
		units.addAll(squad.getUnits());
		squad.clearUnits();
	}
	public void removeUnit(MyUnit u) {
		units.remove(u);
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
		if(center != null && center.getApproxDistance(pos) <  range-16+16*Math.ceil(units.size()/12))
			return true;

		for(MyUnit u: units) {
			if(u.getUnit().getPosition().getApproxDistance(pos) >= range + 3*Math.ceil(units.size()/12)*32) {
				return false;
			}
		}		
		return true;
	}
	
	public int getUnitCount(UnitType type) {
		if(unitTab.containsKey(type))
			return unitTab.get(type);
		else
			return  0;
	}
	public int getUnitCount() {
		return units.size();
	}
}
