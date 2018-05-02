import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwta.BWTA;
import bwta.Region;

public class MyBase {
	private Squad defenders;
	private HashMap<Unit, Integer> resources;
	private ArrayList<Unit> buildings;
	private Squad workers;
	private Game game;
	private boolean isAMain;
	private Region region;
	private boolean isUnderAttack;
	
	public MyBase(Game game, Unit unit) {
		this.game = game;
		defenders = new Squad(game);
		workers = new Squad(game);
		resources = new HashMap<Unit, Integer>();
		buildings = new ArrayList<Unit>();
		this.region = BWTA.getRegion(unit.getPosition());
		isUnderAttack = false;
		workers.resumeMining();
	}
	
	public void clear() {
		resources.clear();
		buildings.clear();
		isUnderAttack = false;
		workers.resumeMining();
	}
	
	public void addBuilding(Unit u) {
		buildings.add(u);
	}
	public void putResource(Unit u, int workers) {
		resources.put(u, workers);
	}
	
	public void add(MyUnit u) {
		UnitType type = u.getUnit().getType();
		
		if(type == UnitType.Protoss_Probe) 
			workers.add(u);
		else 
			defenders.add(u);
	}
	
	public HashMap<Unit, Integer> getResources() {
		return resources;
	}
	public int getPatchCount() {
		int count = 0;
		for(Unit neutralUnit: resources.keySet()) {
			if(neutralUnit.getType().isMineralField())
				count ++;
		}
		return count;
	}
	
	public Squad getWorkers() {
		return workers;
	}
	
	public int getAllBuildingCount() {
		return buildings.size();
	}
	
	public int getAllBuildingCount(UnitType type) {
		Iterator<Unit> itr = buildings.iterator();
		Unit u;
		int count = 0;
		while(itr.hasNext()) {
			u = itr.next();
			if(u.getType() == type)
				count++;
		}
		return count;
	}
	
	public int getCompletedBuildingCount(UnitType type) {
		Iterator<Unit> itr = buildings.iterator();
		Unit u;
		int count = 0;
		while(itr.hasNext()) {
			u = itr.next();
			if(u.isCompleted() && u.getType() == type)
				count++;
		}
		return count;
	}
	
	public boolean isUnderAttack() {
		return isUnderAttack;
	}
	
	public void setUnderAttack(boolean attacked) {
		isUnderAttack = attacked;
	}
}
