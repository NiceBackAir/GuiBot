import java.util.HashMap;
import java.util.Map;

import bwapi.Color;
import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.PositionedObject;
import bwapi.Race;
import bwapi.TechType;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitCommand;
import bwapi.UnitCommandType;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwapi.WeaponType;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class MyUnit extends PositionedObject{
	private Unit u;
	private boolean isHovering = false;
	private boolean isBuilding = false;
	private boolean canAttack = false;
	private boolean isWorker = false;
	
	public static MyUnit createFrom(Unit u) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		MyUnit unit = new MyUnit(u);
		return unit;
	}
	
	public MyUnit(Unit u) {
		if (u == null) {
			throw new RuntimeException("MyUnit: unit is null");
		}
		
		this.u = u;
	}

	@Override
	public Position getPosition() {
		// TODO Auto-generated method stub
		return null;
	}
}
