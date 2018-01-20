import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class GuiBot extends DefaultBWListener {

    private Mirror mirror = new Mirror();
    
    private Game game;

    private Player self;

    private int nextBuildingX = 0;
    private int nextBuildingY = 0;
    
    public static final int PIXELS_PER_TILE = 32;
    public static final float SECONDS_PER_FRAME = 0.042f;
    
    private float mineralsPerFrame = 0f;
    private float gasPerFrame = 0f;
    
    private int reservedMinerals = 0;
    private int reservedGas = 0;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit discovered " + unit.getType());
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
        
        nextBuildingX = self.getStartLocation().getX();
        nextBuildingY = self.getStartLocation().getY() + 5;
    }

    @Override
    public void onFrame() {
        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());

        countUnits();
        
        for (Unit myUnit : self.getUnits()) {
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus) {
            	if(myUnit.isTraining()) {
            		//make sure there will be enough money to build the next probe
            		reservedMinerals = (int) Math.max(0, UnitType.Protoss_Probe.mineralPrice() - myUnit.getRemainingTrainTime() * mineralsPerFrame);
            		game.drawTextScreen(250, 0, "Reserved Minerals: " + reservedMinerals);
            	} else if(self.minerals() >= 50) {
            		myUnit.train(UnitType.Protoss_Probe);
            	}
            }
        }
        
        gather();
        
        buildBuildings();
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
            } 
        }
        game.drawTextScreen(250, 15, "Minerals Per Frame: " + mineralsPerFrame);
        
        //draw my units on screen
        game.drawTextScreen(10, 25, units.toString());
    }

    public void trainAndResearch () {
    	
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
    public void buildBuildings() {
    	TilePosition buildingLoc = new TilePosition(nextBuildingX,nextBuildingY);
    	int tries = 0;
    	while(!game.canBuildHere(buildingLoc, UnitType.Protoss_Pylon) && tries < 7) {
    		nextBuildingX += 2;
    		buildingLoc = new TilePosition(nextBuildingX,nextBuildingY);           
    		tries ++;		
    	}  
    	int shortestDist = 999;
    	Unit closestWorker = null;
    	for (Unit myUnit : self.getUnits()) {
            //build buildings at the optimal time
            if (myUnit.getType().isWorker()) {
            	int dist = myUnit.getDistance(buildingLoc.toPosition());
            	if (dist < shortestDist) {
            		closestWorker = myUnit;
            	}
            }
        }
    	   	
    	int mineralsWhileMoving = (int) (timeToMove(UnitType.Protoss_Probe, shortestDist) * mineralsPerFrame);
    	
    	if (self.minerals() >= 100 + reservedMinerals - mineralsWhileMoving)
    		closestWorker.build(UnitType.Protoss_Pylon, buildingLoc);
    }
    
    /*solve the quadratic formula to determine time in frames needed to travel a distance d with a unit
    	given its acceleration a and top speed v. All distances are in pixels
    */
    public static int timeToMove(UnitType u, float d) {
    	float a = u.acceleration();
    	double v = u.topSpeed();
    	return (int)((-v + Math.sqrt(v*v + 2*a*d))/a);
    }
    
    public static void main(String[] args) {
        new GuiBot().run();
    }
}

