import java.util.Arrays;

import bwapi.DefaultBWListener;
import bwapi.Game;
import bwapi.Mirror;
import bwapi.Player;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.UpgradeType;
import bwta.BWTA;
import bwta.BaseLocation;

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;

    private UnitType nextBuilding = null;
    private int nextBuildingX = 0;
    private int nextBuildingY = 0;
    
    public static final int PIXELS_PER_TILE = 32;
    public static final float SECONDS_PER_FRAME = 0.042f;
    
    private float mineralsPerFrame = 0f;
    private float gasPerFrame = 0f;
    
    private int reservedMinerals = 0;
    private int reservedGas = 0;
    
    //21 Nexus build
    private UnitType[] PvTBO;
    private UnitType[] selectedBO = PvTBO; 
    private boolean[] BOChecklist;
    
    private int buildProgress = -1;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        //System.out.println("New unit discovered " + unit.getType());
    }
    
    @Override
    public void onUnitComplete(Unit unit) {        
        /** TODO move probes between gas and minerals if they're killed
         * 
         */
        if(unit.getType() == UnitType.Protoss_Assimilator) {
        	int gasCounter = 0;
        	for (Unit myUnit: self.getUnits()) {
        		if (gasCounter <3 && myUnit.getType().isWorker() && (myUnit.isGatheringMinerals() || myUnit.isIdle())) {
        			myUnit.gather(unit, false);
        			gasCounter++;
        		}
        	}
        } else if(unit.getType() == UnitType.Protoss_Robotics_Facility) {
        	System.out.println("Build completed in: " + game.elapsedTime() + " seconds.");
        }
    }
    
    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
        
        //player control enable
        game.enableFlag(1);
        game.setLocalSpeed(1);
        
        PvTBO = new UnitType[]{UnitType.Protoss_Nexus,
				UnitType.Protoss_Pylon, 
				UnitType.Protoss_Gateway, 
				UnitType.Protoss_Assimilator, 
				UnitType.Protoss_Cybernetics_Core,
				UnitType.Protoss_Pylon,
				UnitType.Protoss_Nexus,
				UnitType.Protoss_Gateway,
				UnitType.Protoss_Pylon,
				UnitType.Protoss_Robotics_Facility};
        
        selectedBO = PvTBO;
        
        BOChecklist = new boolean[selectedBO.length];
        
        nextBuildingX = self.getStartLocation().getX();
        nextBuildingY = self.getStartLocation().getY() + 4;
    }

    @Override
    public void onFrame() {
        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());
        
        Arrays.fill(BOChecklist, false);
        reservedMinerals = 0;
        reservedGas = 0;

        countUnits();        
        trainAndResearch();        
        gather();        
        
        int i = 0;
        boolean keepBuilding = true;
        while (i<BOChecklist.length && keepBuilding) {
        	if(!BOChecklist[i]) {
        		nextBuilding = selectedBO[i];
        		buildBuilding(nextBuilding, nextBuildingX, nextBuildingY);        	
        		keepBuilding = false;
        	}
        	i++;
        }  
        game.drawTextScreen(250, 30, "Next Building: " + nextBuilding.toString());
    }
    
    public void countUnits() {
        StringBuilder units = new StringBuilder("My units:\n");
        mineralsPerFrame = 0;
        gasPerFrame = 0;
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");
            if (myUnit.getType().isWorker()) {
            	if (myUnit.isGatheringMinerals()) {
            		mineralsPerFrame += 0.0474;   
            	} else if (myUnit.isGatheringGas()) {
            		gasPerFrame += 0.072;
            	}
            } else if(myUnit.getType().isBuilding()) {
            	boolean match = false;
            	int i = 0;
            	while(!match && i<BOChecklist.length) {
            		if (PvTBO[i] == myUnit.getType() && !BOChecklist[i]) {
            			BOChecklist[i] = true;  
            			match = true;
            		}
            		i++;
            	}
            }
        }
        game.drawTextScreen(10, 30, PvTBO[0].toString() + ": " + BOChecklist[0] + "\n"
        		+ PvTBO[1].toString() + ": " + BOChecklist[1] + "\n"
        		+ PvTBO[2].toString() + ": " + BOChecklist[2] + "\n"
        		+ PvTBO[3].toString() + ": " + BOChecklist[3] + "\n"
        		+ PvTBO[4].toString() + ": " + BOChecklist[4] + "\n"
        		+ PvTBO[5].toString() + ": " + BOChecklist[5] + "\n"
        		+ PvTBO[6].toString() + ": " + BOChecklist[6] + "\n"
        		+ PvTBO[7].toString() + ": " + BOChecklist[7] + "\n"
        		+ PvTBO[8].toString() + ": " + BOChecklist[8] + "\n"
        		+ PvTBO[9].toString() + ": " + BOChecklist[9] + "\n"
        		);
        //game.drawTextScreen(250, 15, "Minerals Per Frame: " + mineralsPerFrame);
        
        //draw my units on screen
        //game.drawTextScreen(10, 25, units.toString());
    }

    public void trainAndResearch () {
        for (Unit myUnit : self.getUnits()) {
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && myUnit.isCompleted()) {
            	if(myUnit.isTraining()) {
            		//make sure there will be enough money to build the next probe
            		reservedMinerals += (int) Math.max(0, UnitType.Protoss_Probe.mineralPrice() 
            				- myUnit.getRemainingTrainTime() * mineralsPerFrame);
            	} else if(game.canMake(UnitType.Protoss_Probe)) {
            		myUnit.train(UnitType.Protoss_Probe);
            	} else {
            		reservedMinerals += UnitType.Protoss_Probe.mineralPrice();
            	}
            }
            if (myUnit.getType() == UnitType.Protoss_Gateway && myUnit.canTrain(UnitType.Protoss_Dragoon) && myUnit.isCompleted()) {
            	if(myUnit.isTraining()) {
            		//make sure there will be enough money to build the next probe       		
            		reservedMinerals += (int) Math.max(0, UnitType.Protoss_Dragoon.mineralPrice() 
            				- myUnit.getRemainingTrainTime() * mineralsPerFrame);
            		reservedGas += (int) Math.max(0, UnitType.Protoss_Dragoon.gasPrice() 
            				- myUnit.getRemainingTrainTime() * gasPerFrame);
            	} else if(game.canMake(UnitType.Protoss_Dragoon)) {
            		myUnit.train(UnitType.Protoss_Dragoon);
            	} else {
            		reservedMinerals += UnitType.Protoss_Dragoon.mineralPrice();
            		reservedGas += UnitType.Protoss_Dragoon.gasPrice();
            	}
            }        
            if (myUnit.getType() == UnitType.Protoss_Cybernetics_Core) {
            	if (myUnit.isCompleted()) {
	            	if (!myUnit.isTraining()) {	            	
		            	if(canAfford(UpgradeType.Singularity_Charge)) {
		            		myUnit.upgrade(UpgradeType.Singularity_Charge);
		            	}
	            	} else if(self.getUpgradeLevel(UpgradeType.Singularity_Charge) == 0) {	            	
	            		reservedMinerals += UpgradeType.Singularity_Charge.mineralPrice();
	            		reservedGas += UpgradeType.Singularity_Charge.gasPrice();		            	
	            	}        	
	            } else {
	            	reservedMinerals += Math.max(0, UpgradeType.Singularity_Charge.mineralPrice()
	            		- myUnit.getRemainingBuildTime() * mineralsPerFrame);
            		reservedGas += Math.max(0, UpgradeType.Singularity_Charge.gasPrice()
            			- myUnit.getRemainingBuildTime() * gasPerFrame);
	            }
        	}
        }
        game.drawTextScreen(250, 0, "Reserved Minerals: " + reservedMinerals);
        game.drawTextScreen(250, 15, "Reserved Gas: " + reservedGas);
    }
    public void gather() {
    	for (Unit myUnit : self.getUnits()) {
            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker() && myUnit.isIdle()) {
                Unit closestMineral = null;

                //find the closest mineral
                for (Unit neutralUnit : game.neutral().getUnits()) {
                    if (neutralUnit.getType().isMineralField()) {
                        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                            closestMineral = neutralUnit;
                        }
                    }
                }

                //if a mineral patch was found, send the worker to gather it
                if (closestMineral != null) {
                    myUnit.gather(closestMineral, false);
                }
            }
        }
    }
    
    //build a pylon at the optimal time
    //returns true if build command was issued, else returns false 
    public boolean buildBuilding(UnitType building, int x, int y) {   
    	TilePosition buildingLoc = new TilePosition(x,y);
    	if (building == UnitType.Protoss_Assimilator) {
    		TilePosition closestGas = null;
            for (Unit neutralUnit : game.neutral().getUnits()) {            	
                if (neutralUnit.getType() == UnitType.Resource_Vespene_Geyser) {
                	if (game.canBuildHere(neutralUnit.getTilePosition(), building) && (closestGas == null || self.getStartLocation().getDistance(neutralUnit.getTilePosition())
                			< self.getStartLocation().getDistance(closestGas))) {
                		closestGas = neutralUnit.getTilePosition();
                	}
                }
            }    
            buildingLoc = closestGas;
    	} else if (building == UnitType.Protoss_Nexus) {    		
    		TilePosition closestBase = null;
            for (BaseLocation b : BWTA.getBaseLocations()) {  
            	TilePosition t = b.getTilePosition();
        		if (game.canBuildHere(t, building) && (closestBase == null || self.getStartLocation().getDistance(t)
            			< self.getStartLocation().getDistance(closestBase))) {
            		closestBase = t;
            	}
            }    
            buildingLoc = closestBase;
    	} else if (building == UnitType.Protoss_Pylon) {
	    	int tries = 0;
	    	while(!game.canBuildHere(buildingLoc, building) && tries < 10) {
	    		x+=6;
	    		buildingLoc = new TilePosition(x,y);           
	    		tries++;		
	    	}
    	} else {
	    	int tries = 0;
	    	y+=2;
	    	buildingLoc = new TilePosition(x,y);
	    	while(!game.canBuildHere(buildingLoc, building) && tries < 20) {
	    		x++;
	    		buildingLoc = new TilePosition(x,y);           
	    		tries++;		
	    	}  
    	}
    	
    	int shortestDist = 999999;
    	Unit closestWorker = null;
    	for (Unit myUnit : self.getUnits()) {
            //build buildings at the optimal time
            if (myUnit.getType().isWorker() && !myUnit.isGatheringGas()) {
            	int dist = myUnit.getDistance(buildingLoc.toPosition());
            	if (dist < shortestDist) {
            		closestWorker = myUnit;
            		shortestDist = dist;
            	}
            }
        }
    	   	
    	int mineralsWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * mineralsPerFrame);
    	int gasWhileMoving = (int) (shortestDist/UnitType.Protoss_Probe.topSpeed() * gasPerFrame);
    	
    	if (canAfford(building, mineralsWhileMoving, gasWhileMoving)) {
    		if(game.isExplored(buildingLoc)) {     
    			closestWorker.build(building, buildingLoc);
    		} else {
    			closestWorker.move(buildingLoc.toPosition());    			
    		}
    		return true;
    	} else {
    		return false;
    	}
    }
    
    /*solve the quadratic formula to determine time in frames needed to travel a distance d with a unit
    	given its acceleration a and top speed v. All distances are in pixels. So far assumes you start from no movement
    	
    	Not used yet because probes accelerate too fast
    */
    public static int timeToMove(UnitType u, float d) {
    	float a = u.acceleration();
    	double v = u.topSpeed();
    	double timeToTopSpeed = v/a;
    	double distToTopSpeed = 0.5*a*Math.pow(timeToTopSpeed,2);
    	if(d <= distToTopSpeed) {
    		return (int) Math.sqrt(2*d/a);
    	} else {
    		return (int) (timeToTopSpeed + (d-distToTopSpeed)/v);
    	}	
    	
    }
    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UnitType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UnitType u, int mineralsWhileMoving, int gasWhileMoving) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals - mineralsWhileMoving
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas - gasWhileMoving));
    }    
    //tells you if you can afford to build something without cutting higher priority units
    public boolean canAfford(UpgradeType u) {
    	return (self.minerals() >= u.mineralPrice() + reservedMinerals
    			&& (u.gasPrice() == 0 || self.gas() >= u.gasPrice() + reservedGas));
    }
    
    public static void main(String[] args) {
        new GuiBot().run();
    }
}

