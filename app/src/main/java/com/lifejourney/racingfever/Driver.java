package com.lifejourney.racingfever;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;

import com.lifejourney.engine2d.CollidableObject;
import com.lifejourney.engine2d.Line;
import com.lifejourney.engine2d.PointF;
import com.lifejourney.engine2d.RectF;
import com.lifejourney.engine2d.Vector2D;

import java.util.ArrayList;
import java.util.Collections;

public class Driver implements Comparable<Driver> {

    private static final String LOG_TAG = "Driver";

    public static class Builder {
        String name;
        ArrayList<CollidableObject> obstacles;
        ArrayList<Car> cars;

        // Optional parameter
        Builder(String name) {
            this.name = name;
        }
        Builder obstacles(ArrayList<CollidableObject> obstacles) {
            this.obstacles = obstacles;
            return this;
        }
        Builder cars(ArrayList<Car> cars) {
            this.cars = cars;
            return this;
        }
        public Driver build() {
            return new Driver(this);
        }
    }

    private enum State {
        STOP(Integer.MAX_VALUE),
        CRUISING(Integer.MAX_VALUE),
        DEFENSIVE_DRIVING(60, CRUISING),
        AGGRESSIVE_DRIVING(30, CRUISING),
        EMERGENCY_ESCAPING(10, CRUISING),
        OVERTAKING(150, CRUISING);

        State(int maxStayingTime) {
            this.maxStayingTime = maxStayingTime;
            this.returnState = this;
        }

        State(int maxStayingTime, State returnState) {
            this.maxStayingTime = maxStayingTime;
            this.returnState = returnState;
        }

        public int maxStayingTime() {
            return maxStayingTime;
        }

        public State returnState() {
            return returnState;
        }

        private final int maxStayingTime;
        private final State returnState;
    }

    private Driver(Builder builder) {
        name = builder.name;
        obstacles = builder.obstacles;
        cars = builder.cars;
    }

    /**
     *
     */
    public void close() {
        if (waypointLine != null) {
            waypointLine.close();
        }
        if (leftLaneCheckingLine != null) {
            leftLaneCheckingLine.close();
        }
        if (rightLaneCheckingLine != null) {
            rightLaneCheckingLine.close();
        }
        if (lastPassedWaypointLineL != null) {
            lastPassedWaypointLineL.close();
            lastPassedWaypointLineR.close();
            lastPassedWaypointLineT.close();
            lastPassedWaypointLineB.close();
        }
        if (targetWaypointLineL != null) {
            targetWaypointLineL.close();
            targetWaypointLineR.close();
            targetWaypointLineT.close();
            targetWaypointLineB.close();
        }
    }

    /**
     *
     */
    public void update() {
        if (myCar == null) {
            return;
        }

        // Checking if the car passes the finish line
        if (!finishLineCheckerDone && checkFinishLinePassing()) {
            lap++;
            Log.i(LOG_TAG, name + " lap: " + lap + " rank: " + rank);
            finishLineCheckerDone = true;
        }
        else if (finishLineCheckerDone && lastWaypointPassedIndex < 10) {
            finishLineCheckerDone = false;
        }

        if (!myCar.isUpdatePossible()) {
            return;
        }

        // Driver the car
        drive();
    }

    /**
     *
     * @param car
     */
    void ride(Car car) {
        this.myCar = car;
        this.myCar.setDriver(this);
    }

    /**
     *
     * @param track
     */
    void learn(Track track) {
        this.track = track;
    }

    /**
     *
     */
    void start() {
        state = State.CRUISING;
        setWaypointToTarget(targetWaypointIndex);
    }

    /**
     *
     */
    void stop() {
        myCar.stop();
    }

    /**
     *
     * @param other
     * @return
     */
    @Override
    public int compareTo(@NonNull Driver other) {
        if (other == this) {
            return 0;
        }
        else {
            return comparePositionAhead(other);
        }
    }

    /**
     *
     * @param other
     * @return
     */
    int compareRanking(Driver other) {
        if (other.getLap() < this.getLap()) {
            return -1;
        }
        else if (other.getLap() > this.getLap()) {
            return 1;
        }
        else {
            return comparePositionAhead(other);
        }
    }

    /**
     *
     * @param other
     * @return
     */
    private int comparePositionAhead(Driver other) {
        int otherLastWaypointIndexInOptimal = other.lastWaypointPassedIndex;
        int thisLastWaypointIndexInOptimal = this.lastWaypointPassedIndex;

        if (otherLastWaypointIndexInOptimal < thisLastWaypointIndexInOptimal) {
            return -1;
        }
        else if (otherLastWaypointIndexInOptimal > thisLastWaypointIndexInOptimal) {
            return 1;
        }
        else {
            int waypointCount = track.getLane(Track.LaneSelection.OPTIMAL_LANE).size();
            int nextPassingWaypointIndex = (thisLastWaypointIndexInOptimal + 1) % waypointCount;
            PointF nextWaypointPt = track.getWaypointRegion(Track.LaneSelection.OPTIMAL_LANE,
                    nextPassingWaypointIndex).center();

            float distanceSqToNextWaypointForOther =
                    other.myCar.getPosition().distanceSq(nextWaypointPt);
            float distanceSqToNextWaypointForThis =
                    this.myCar.getPosition().distanceSq(nextWaypointPt);

            return Float.compare(distanceSqToNextWaypointForThis,
                    distanceSqToNextWaypointForOther);
        }
    }

    /**
     *
     * @return
     */
    private boolean checkFinishLinePassing() {
        for (int i = 0; i < track.getData().getFinishPointCount(); ++i) {
            RectF finishLineRegion =
                    track.getView().getScreenRegionfromTrackCoord(track.getData().getFinishPoint(i));
            if (finishLineRegion.includes(myCar.getPosition())) {
                return true;
            }
        }

        return false;
    }

    /**
     *
     * @param targetRegion
     */
    private void setTargetRegion(RectF targetRegion) {
        this.targetRegion = targetRegion;
    }

    /**
     *
     * @return
     */
    private boolean checkIfCarArrivesTargetRegion() {
        if (targetRegion == null)
            return false;

        return targetRegion.includes(myCar.getPosition());
    }

    /**
     *
     */
    private void updateLastPassedWaypoint() {
        int waypointCount = track.getLane(laneSelection).size();
        int numberOfWaypointsToTest;
        if (targetWaypointIndex < lastWaypointPassedIndex) {
            numberOfWaypointsToTest = waypointCount -
                    lastWaypointPassedIndex + targetWaypointIndex + 1;
        }
        else {
            numberOfWaypointsToTest = targetWaypointIndex - lastWaypointPassedIndex + 1;
        }

        PointF position = myCar.getPosition();
        float nearestDistance = Float.MAX_VALUE;
        int nearestWaypointIndex = -1;
        for (int i = numberOfWaypointsToTest - 1; i >= 0; --i) {
            int currentWaypointIndex = (lastWaypointPassedIndex + i) % waypointCount;
            RectF region = track.getWaypointRegion(laneSelection, currentWaypointIndex);

            if (region.includes(position)) {
                lastWaypointPassedIndex = currentWaypointIndex;
                return;
            }
            else {
                float distance = region.center().distance(position);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestWaypointIndex = currentWaypointIndex;
                }
            }
        }

        if (nearestWaypointIndex != -1) {
            lastWaypointPassedIndex = nearestWaypointIndex;
        }
    }

    /**
     *
     * @param laneSelection
     * @param currentIndex
     * @param targetIndex
     * @param maxSearchableScore
     * @return
     */
    private int findSuitableWaypointForNewTarget(Track.LaneSelection laneSelection,
                                                 int currentIndex,
                                                 int targetIndex,
                                                 int maxSearchableScore) {
        ArrayList<Integer> candidatesWaypoints = new ArrayList<>();

        int maxSearchableCount =
                track.getWaypointCountWhichCanBeSearched(laneSelection, currentIndex, maxSearchableScore);

        if (track.getDistanceBetweenWaypointIndex(laneSelection, currentIndex, targetIndex) >
                maxSearchableCount) {
            return -1;
        }

        // Search through waypoints
        int waypointCount = track.getLane(laneSelection).size();
        for (int i = 1; i < maxSearchableCount; ++i) {
            candidatesWaypoints.add((currentIndex + i) % waypointCount);
        }

        // Raytracing waypoints to find possible one
        Collections.reverse(candidatesWaypoints);
        for (int waypointIndex : candidatesWaypoints) {
            PointF targetCenter = track.getWaypointRegion(laneSelection, waypointIndex).center();
            float distanceToWaypoint = targetCenter.distance(myCar.getPosition());

            if (track.getNearestDistanceToRoadBlock(myCar.getPosition(), targetCenter) >=
                    distanceToWaypoint) {
                return waypointIndex;
            }
        }

        // Failed to find suitable waypoint index
        return -1;
    }

    /**
     *
     */
    private void setNewWaypointToTarget() {
        int newWaypoint = findSuitableWaypointForNewTarget(laneSelection,
                lastWaypointPassedIndex, targetWaypointIndex, 0);
        if (newWaypoint == -1) {
            newWaypoint = track.findNextValidWaypoint(laneSelection, lastWaypointPassedIndex);
        }

        setWaypointToTarget(newWaypoint);
    }

    /**
     *
     */
    private void setNewWaypointToTargetOnlyIfSuitable() {
        int newWaypoint = findSuitableWaypointForNewTarget(laneSelection,
                lastWaypointPassedIndex, targetWaypointIndex, 0);
        if (newWaypoint != -1) {
            setWaypointToTarget(newWaypoint);
        }
    }

    /**
     *
     * @param waypointIndex
     */
    private void setWaypointToTarget(int waypointIndex) {
        setTargetRegion(track.getWaypointRegion(laneSelection, waypointIndex));
        targetWaypointIndex = waypointIndex;

        /*
        Log.i(LOG_TAG, name + " " + currentLaneSelection.ordinal() +
                " currentTargetWaypointIndex: " + currentTargetWaypointIndex +
                " " + lastWaypointPassedIndex);
         */
    }

    /**
     *
     */
    private void updateWaypoint() {
        // Check if target region is achieved
        if (track == null) {
            if (checkIfCarArrivesTargetRegion())
                targetRegion = null;
        }
        else {
            updateLastPassedWaypoint();
            if (lastWaypointPassedIndex == targetWaypointIndex) {
                // Go to next waypoint
                setNewWaypointToTarget();
            }
            else {
                // Go to next waypoint only if possible
                if (nextWaypointSearchTimeLeft <= 0) {
                    setNewWaypointToTargetOnlyIfSuitable();
                    nextWaypointSearchTimeLeft = MIN_WAYPOINT_SEARCH_PERIOD;
                }
                else {
                    nextWaypointSearchTimeLeft--;
                }
            }
        }
    }

    /**
     *
     * @param weight
     */
    private void driveAlongTheWay(float weight) {
        weight += (Math.random()%0.2f) - 0.1f;
        myCar.seek(targetRegion.center(), weight);
    }

    /**
     *
     * @param frontAngle
     * @param maxForwardDistance
     * @param maxBackwardDistance
     * @return
     */
    private ArrayList<CollidableObject> getNeighborObstacles(float frontAngle,
                                                             float maxForwardDistance,
                                                             float maxBackwardDistance) {

        ArrayList<CollidableObject> neighborObstacles = new ArrayList<>();

        if (obstacles == null) {
            return neighborObstacles;
        }

        Vector2D myPositionVector = myCar.getPositionVector();

        for (CollidableObject obstacle: obstacles) {
            if (obstacle == myCar) {
                continue;
            }

            Vector2D offset = obstacle.getPositionVector().subtract(myPositionVector);
            Vector2D unitOffset = offset.clone().normalize();
            if (myCar.getForwardVector().angle(unitOffset) < frontAngle) {
                if (offset.length() <= maxForwardDistance) {
                    neighborObstacles.add(obstacle);
                    if (frontAngle == 10.0f) {
                        Log.e(LOG_TAG, frontAngle + " " + offset.length() + " " + maxForwardDistance);
                    }
                }
            }
            else {
                if (offset.length() <= maxBackwardDistance) {
                    neighborObstacles.add(obstacle);
                }
            }
        }

        return neighborObstacles;
    }

    /**
     *
     * @return
     */
    private Car.AvoidingState avoidObstacles() {
        float maxForwardDistance = myCar.getMovingDistanceForOneUpdate() * 6;
        float maxBackwardDistance = 0; //distanceForOneUpdate * 2;
        ArrayList<CollidableObject> neighborObstacles = getNeighborObstacles(180.0f,
                maxForwardDistance, maxBackwardDistance);

        if (neighborObstacles.size() == 0) {
            return Car.AvoidingState.NO_OBSTACLE;
        }

        return myCar.avoidObstacles(neighborObstacles, maxForwardDistance,
                maxBackwardDistance, track);
    }

    /**
     *
     */
    private void tryOvertaking() {
        if (rank == 0) {
            return;
        }

        // Check if it can go to overtaking state
        float overDrivingScore = OVERTAKING_ENTER_POSSIBILITY;
        float maxDistance = myCar.getMovingDistanceForOneUpdate() * 5;
        CollidableObject frontObstacle = getNearestFrontObstacle(maxDistance);
        if (frontObstacle == null) {
            overDrivingScore += OVERTAKING_ENTER_POSSIBILITY;
        } else if (frontObstacle.getVelocity().length() < myCar.getMaxVelocity()) {
            overDrivingScore += OVERTAKING_ENTER_POSSIBILITY;
        }

        if (Math.random() < overDrivingScore) {
            if (frontObstacle == null) {
                setPathSelection(Track.LaneSelection.MIDDLE_LANE);
                transition(State.OVERTAKING);
            } else {
                Track.LaneSelection laneSelection = chooseOvertakingPath();
                if (laneSelection != Track.LaneSelection.OPTIMAL_LANE) {
                    setPathSelection(laneSelection);
                    transition(State.OVERTAKING);
                    if (leftLaneCheckingLine == null) {
                        assert leftLaneCheckingLine != null;
                        leftLaneCheckingLine.commit();
                    }
                    if (rightLaneCheckingLine == null) {
                        assert rightLaneCheckingLine != null;
                        rightLaneCheckingLine.commit();
                    }
                }
            }
        }
    }

    /**
     *
     * @param state
     */
    private void transition(State state) {
        this.state = state;
        stayingTimeLeftOnState = state.maxStayingTime();
        stayingTimeOnState = 0;
        defensiveDrivingReleaseCount = 0;
    }

    /**
     *
     */
    private void onCruising() {
        myCar.circleShape.setColor(1.0f, 1.0f, 1.0f);

        // set to optimal path
        setPathSelection(Track.LaneSelection.MIDDLE_LANE);

        // Update waypoint target
        updateWaypoint();

        // Drive to the target waypoint
        driveAlongTheWay(0.7f);

        // Keep distance with front vehicle
        float maxDistance = myCar.getMovingDistanceForOneUpdate() * 6;
        CollidableObject frontObstacle = getNearestFrontObstacle(maxDistance);
        if (frontObstacle instanceof Car) {
            myCar.getVelocity().truncate(frontObstacle.getVelocity()
                    .dot(myCar.getForwardVector())*0.9f);
        }

        // Avoid collision
        Car.AvoidingState state = avoidObstacles();
        if (state == Car.AvoidingState.AVOIDING ||
            state == Car.AvoidingState.BRAKING) {
            transition(State.DEFENSIVE_DRIVING);
            setPathSelection(chooseDefensivePath());
            return;
        }

        // Try overtaking
        tryOvertaking();
    }

    /**
     *
     */
    private void onDefensiveDriving() {
        myCar.circleShape.setColor(0.0f, 1.0f, 0.0f);

        // Update waypoint target
        updateWaypoint();

        // Drive to the target waypoint
        driveAlongTheWay(0.5f);

        // Avoid collision
        Car.AvoidingState state = avoidObstacles();

        if (state == Car.AvoidingState.NO_OBSTACLE) {
            defensiveDrivingReleaseCount++;
            if (defensiveDrivingReleaseCount > DEFENSIVE_DRIVING_RELEASE_COUNT) {
                transition(State.CRUISING);
                defensiveDrivingReleaseCount = 0;
            }
        }
        else {
            // if it's too slow downed, let's go to emergency escaping mode
            if (stayingTimeOnState > State.DEFENSIVE_DRIVING.maxStayingTime() / 2 &&
                    myCar.getVelocity().length() < EMERGENCY_ESCAPING_STATE_VELOCITY_LIMIT) {
                transition(State.EMERGENCY_ESCAPING);
                setPathSelection(chooseDefensivePath());
            }
        }

        // Try overtaking
        tryOvertaking();
    }

    /**
     *
     */
    private void onEmergencyEscaping() {
        myCar.circleShape.setColor(1.0f, 0.0f, 1.0f);

        // Update waypoint target
        updateWaypoint();

        // Driver to the target waypoint
        driveAlongTheWay(1.0f);
    }

    /**
     *
     */
    @SuppressLint("Assert")
    private void onOvertaking() {
        myCar.circleShape.setColor(1.0f, 0.0f, 0.0f);

        // If car collided, transition to defensive driving
        if (myCar.isCollided()) {
            transition(State.DEFENSIVE_DRIVING);
            return;
        }

        // Update waypoint target
        updateWaypoint();

        // Go faster
        driveAlongTheWay(1.0f);

        // Avoid collision
        Car.AvoidingState state = avoidObstacles();
        if (state == Car.AvoidingState.BRAKING) {
            // Reduce tick counts faster if it takes brake
            tickTransitionTime(OVERTAKING_PENALTY_ON_BRAKING);

            // Try alternative path
            Track.LaneSelection laneSelection = chooseOvertakingPath();
            if (laneSelection != Track.LaneSelection.OPTIMAL_LANE) {
                setPathSelection(laneSelection);
                if (leftLaneCheckingLine == null) {
                    assert false;
                    leftLaneCheckingLine.commit();
                }
                if (rightLaneCheckingLine == null) {
                    assert false;
                    rightLaneCheckingLine.commit();
                }
            }
        }

        // Check if the lane is releaseAllTextures
        if (stayingTimeLeftOnState == 0) {
           if (rank > 0 && !checkWaypointTargetIsBlocked() &&
                   Math.random() < OVERTAKING_EXTEND_POSSIBILITY) {
               extendTransitionTime(OVERTAKING_EXTEND_TICKCOUNT);
           }
        }
    }

    /**
     *
     * @return
     */
    private boolean checkWaypointTargetIsBlocked() {
        PointF targetPt = targetRegion.center();

        // Get neighbors
        float distanceForOneUpdate = myCar.getMovingDistanceForOneUpdate();
        float maxForwardDistance = distanceForOneUpdate * 6;
        float maxBackwardDistance = distanceForOneUpdate * 2;
        ArrayList<CollidableObject> obstacles = getNeighborObstacles(180.0f,
                maxForwardDistance, maxBackwardDistance);

        // Check if obstacle blocks the way
        float nearestDistance = Float.MAX_VALUE;
        float targetDistance = targetPt.distance(myCar.getPosition());
        float direction = targetPt.vectorize().subtract(myCar.getPositionVector()).direction();
        for (CollidableObject obstacle : obstacles) {
            float distance = myCar.checkObstacleCanBeCollided(obstacle, direction, targetDistance,
                    targetDistance);
            if (distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        return (nearestDistance != Float.MAX_VALUE);
    }

    /**
     *
     * @param laneSelection
     * @param obstacles
     * @return
     */
    private float[] checkBoundaryPathToBeMovable(Track.LaneSelection laneSelection,
                                                 ArrayList<CollidableObject> obstacles) {
        // Check next waypoint target on this path
        int currentWaypointIndexOnPath = lastWaypointPassedIndex;
        int newTargetWaypointIndex = findSuitableWaypointForNewTarget(laneSelection,
                currentWaypointIndexOnPath, currentWaypointIndexOnPath, 2);
        if (newTargetWaypointIndex == -1) {
            newTargetWaypointIndex = (currentWaypointIndexOnPath + 1) % track.getLane(laneSelection).size();
        }
        //int newTargetWaypointIndex = (currentWaypointIndexOnPath + 1) % track.getPath(selection).size();
        PointF targetPt = track.getWaypointRegion(laneSelection, newTargetWaypointIndex).center();

        if (laneSelection == Track.LaneSelection.LEFT_BOUNDARY_LANE) {
            if (leftLaneCheckingLine == null) {
                leftLaneCheckingLine = new Line.Builder(myCar.getPosition(), targetPt)
                        .color(1.0f, 0.0f, 0.0f, 1.0f).visible(true).build();
            } else {
                leftLaneCheckingLine.setPoints(myCar.getPosition(), targetPt);
            }
        }
        else if (laneSelection == Track.LaneSelection.RIGHT_BOUNDARY_LANE) {
            if (rightLaneCheckingLine == null) {
                rightLaneCheckingLine = new Line.Builder(myCar.getPosition(), targetPt)
                        .color(1.0f, 0.0f, 0.0f, 1.0f).visible(true).build();
            } else {
                rightLaneCheckingLine.setPoints(myCar.getPosition(), targetPt);
            }
        }

        // Check if obstacle blocks the way
        float nearestDistance = Float.MAX_VALUE;
        float targetDistance = targetPt.distance(myCar.getPosition());
        float direction = targetPt.vectorize().subtract(myCar.getPositionVector()).direction();
        for (CollidableObject obstacle : obstacles) {
            float distance = myCar.checkObstacleCanBeCollided(obstacle, direction, targetDistance,
                    targetDistance);
            if (distance < nearestDistance) {
                nearestDistance = distance;
            }
        }

        float[] result = new float[2];

        // Check road block
        float distanceToRoadBlock = track.getNearestDistanceToRoadBlock(myCar.getPosition(),
                direction, targetDistance);
        if (distanceToRoadBlock > 0.0f && distanceToRoadBlock < Float.MAX_VALUE) {
            result[0] = targetDistance;
            result[1] = 0.0f;
            return result;
        }

        result[0] = targetDistance;
        result[1] = nearestDistance;
        return result;
    }

    /**
     *
     * @return
     */
    private Track.LaneSelection chooseOvertakingPath() {
        // Get neighbors
        float distanceForOneUpdate = myCar.getMovingDistanceForOneUpdate();
        float maxForwardDistance = distanceForOneUpdate * 6;
        float maxBackwardDistance = distanceForOneUpdate * 2;
        ArrayList<CollidableObject> neighborObstacles = getNeighborObstacles(180.0f,
                maxForwardDistance, maxBackwardDistance);

        if (neighborObstacles.size() == 0) {
            return Track.LaneSelection.MIDDLE_LANE;
        }

        // Count vehicles on the each paths
        int vehiclesOnLeftLane = 0, vehiclesOnRightLane = 0, vehiclesOnMiddleLane = 0;
        for (CollidableObject obstacle : neighborObstacles) {
            if (obstacle instanceof Car) {
                Car neighborCar = (Car) obstacle;
                Driver neighborDriver = neighborCar.getDriver();

                Track.LaneSelection selection = neighborDriver.getPathSelection();
                if (selection == Track.LaneSelection.LEFT_BOUNDARY_LANE) {
                    vehiclesOnLeftLane++;
                } else if (selection == Track.LaneSelection.RIGHT_BOUNDARY_LANE) {
                    vehiclesOnRightLane++;
                } else if (selection == Track.LaneSelection.MIDDLE_LANE) {
                    vehiclesOnMiddleLane++;
                }
            }
        }

        if (vehiclesOnLeftLane >= 2 && vehiclesOnRightLane >= 2 && vehiclesOnMiddleLane >= 2) {
            return Track.LaneSelection.OPTIMAL_LANE;
        }

        // Get distance to each boundary path
        float[] nearestDistanceToBlockOnLeftLane =
                checkBoundaryPathToBeMovable(Track.LaneSelection.LEFT_BOUNDARY_LANE,
                        neighborObstacles);
        float[] nearestDistanceToBlockOnRightLane =
                checkBoundaryPathToBeMovable(Track.LaneSelection.RIGHT_BOUNDARY_LANE,
                        neighborObstacles);
        /*
        float[] nearestDistanceToBlockOnMiddleLane =
                checkBoundaryPathToBeMovable(Track.LaneSelection.MIDDLE_LANE,
                        neighborObstacles);
         */

        // Decide lane
        Track.LaneSelection laneSelection;
        float maxAllowedDistance = myCar.getVelocity().length()*myCar.getUpdatePeriod()*6;
        if (nearestDistanceToBlockOnLeftLane[1] < maxAllowedDistance &&
                nearestDistanceToBlockOnRightLane[1] < maxAllowedDistance) {
            laneSelection = Track.LaneSelection.OPTIMAL_LANE;
        }
        else if (nearestDistanceToBlockOnLeftLane[1] < maxAllowedDistance) {
            laneSelection = Track.LaneSelection.RIGHT_BOUNDARY_LANE;
        }
        else if (nearestDistanceToBlockOnRightLane[1] < maxAllowedDistance) {
            laneSelection = Track.LaneSelection.LEFT_BOUNDARY_LANE;
        }
        else {
            if (nearestDistanceToBlockOnLeftLane[0] > nearestDistanceToBlockOnRightLane[0]) {
                laneSelection = Track.LaneSelection.RIGHT_BOUNDARY_LANE;
            } else {
                laneSelection = Track.LaneSelection.LEFT_BOUNDARY_LANE;
            }
        }

        if (laneSelection == Track.LaneSelection.LEFT_BOUNDARY_LANE &&
                vehiclesOnLeftLane >= 3) {
            if (vehiclesOnMiddleLane < 2) {
                laneSelection = Track.LaneSelection.MIDDLE_LANE;
            }
            else {
                laneSelection = Track.LaneSelection.OPTIMAL_LANE;
            }
        }
        else if (laneSelection == Track.LaneSelection.RIGHT_BOUNDARY_LANE &&
                vehiclesOnRightLane >= 3) {
            if (vehiclesOnMiddleLane < 2) {
                laneSelection = Track.LaneSelection.MIDDLE_LANE;
            }
            else {
                laneSelection = Track.LaneSelection.OPTIMAL_LANE;
            }
        }

        return laneSelection;
    }

    /**
     *
     * @return
     */
    private Track.LaneSelection chooseDefensivePath() {
        // Get neighbors
        float distanceForOneUpdate = myCar.getVelocity().length() * myCar.getUpdatePeriod();
        float maxForwardDistance = distanceForOneUpdate * 6;
        float maxBackwardDistance = distanceForOneUpdate * 2;
        ArrayList<CollidableObject> neighborObstacles = getNeighborObstacles(180.0f,
                maxForwardDistance, maxBackwardDistance);

        if (neighborObstacles.size() == 0) {
            return Track.LaneSelection.OPTIMAL_LANE;
        }

        // Get distance to each boundary path
        float[] leftBoundaryResult =
                checkBoundaryPathToBeMovable(Track.LaneSelection.LEFT_BOUNDARY_LANE,
                        neighborObstacles);
        float[] rightBoundaryResult =
                checkBoundaryPathToBeMovable(Track.LaneSelection.RIGHT_BOUNDARY_LANE,
                        neighborObstacles);

        // Decide path
        float maxAllowedDistance = myCar.getVelocity().length()*myCar.getUpdatePeriod()*4;
        if (leftBoundaryResult[1] < maxAllowedDistance &&
                rightBoundaryResult[1] < maxAllowedDistance) {
            return Track.LaneSelection.MIDDLE_LANE;
        }
        else if (leftBoundaryResult[1] < maxAllowedDistance) {
            return Track.LaneSelection.RIGHT_BOUNDARY_LANE;
        }
        else if (rightBoundaryResult[1] < maxAllowedDistance) {
            return Track.LaneSelection.LEFT_BOUNDARY_LANE;
        }
        else {
            if (leftBoundaryResult[1] == Float.MAX_VALUE &&
                rightBoundaryResult[1] == Float.MAX_VALUE) {
                // Count vehicles on the each paths
                int leftBoundaryVehicles = 0, rightBoundaryVehicles = 0;
                for (CollidableObject obstacle : neighborObstacles) {
                    if (obstacle instanceof Car) {
                        Car neighborCar = (Car) obstacle;
                        Driver neighborDriver = neighborCar.getDriver();

                        Track.LaneSelection selection = neighborDriver.getPathSelection();
                        if (selection == Track.LaneSelection.LEFT_BOUNDARY_LANE) {
                            leftBoundaryVehicles++;
                        } else if (selection == Track.LaneSelection.RIGHT_BOUNDARY_LANE) {
                            rightBoundaryVehicles++;
                        }
                    }
                }

                if (leftBoundaryVehicles < rightBoundaryVehicles) {
                    return Track.LaneSelection.LEFT_BOUNDARY_LANE;
                }
                else if (leftBoundaryVehicles > rightBoundaryVehicles) {
                    return Track.LaneSelection.RIGHT_BOUNDARY_LANE;
                }
                else {
                    if (leftBoundaryResult[0] > rightBoundaryResult[0]) {
                        return Track.LaneSelection.RIGHT_BOUNDARY_LANE;
                    }
                    else {
                        return Track.LaneSelection.LEFT_BOUNDARY_LANE;
                    }
                }

            }
            else if (leftBoundaryResult[1] == Float.MAX_VALUE) {
                return Track.LaneSelection.LEFT_BOUNDARY_LANE;
            }
            else if (rightBoundaryResult[1] == Float.MAX_VALUE) {
                return Track.LaneSelection.RIGHT_BOUNDARY_LANE;
            }
        }

        return Track.LaneSelection.MIDDLE_LANE;
    }

    /**
     *
     * @param maxDistance
     * @return
     */
    private CollidableObject getNearestFrontObstacle(float maxDistance) {
        // Get front obstacles
        ArrayList<CollidableObject> frontObstacles = getNeighborObstacles(20.0f,
                maxDistance, 0);

        if (frontObstacles.size() == 0) {
            return null;
        }

        // Find nearest one
        float nearestDistance = Float.MAX_VALUE;
        CollidableObject nearestObstacle = null;
        for (CollidableObject obstacle: frontObstacles) {
            float distance = obstacle.getPosition().distance(myCar.getPosition());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestObstacle = obstacle;
            }
        }

        return nearestObstacle;
    }

    /**
     *
     */
    private void drive() {
        // Debugging
        RectF lastPassedRegion = track.getWaypointRegion(laneSelection, lastWaypointPassedIndex);
        RectF targetRegion = track.getWaypointRegion(laneSelection, targetWaypointIndex);
        if (waypointLine == null) {
            waypointLine = new Line.Builder(myCar.getPosition(), targetRegion.center())
                    .color(0.0f, 1.0f, 1.0f, 1.0f).visible(true).build();
            lastPassedWaypointLineL = new Line.Builder(lastPassedRegion.topLeft(), lastPassedRegion.bottomLeft())
                    .color(0.0f, 1.0f, 1.0f, 1.0f).visible(true).build();
            lastPassedWaypointLineR = new Line.Builder(lastPassedRegion.topRight(), lastPassedRegion.bottomRight())
                    .color(0.0f, 1.0f, 1.0f, 1.0f).visible(true).build();
            lastPassedWaypointLineT = new Line.Builder(lastPassedRegion.topLeft(), lastPassedRegion.topRight())
                    .color(0.0f, 1.0f, 1.0f, 1.0f).visible(true).build();
            lastPassedWaypointLineB = new Line.Builder(lastPassedRegion.bottomLeft(), lastPassedRegion.bottomRight())
                    .color(0.0f, 1.0f, 1.0f, 1.0f).visible(true).build();
            targetWaypointLineL= new Line.Builder(targetRegion.topLeft(), targetRegion.bottomLeft())
                    .color(1.0f, 1.0f, 0.0f, 1.0f).visible(true).build();
            targetWaypointLineR = new Line.Builder(targetRegion.topRight(), targetRegion.bottomRight())
                    .color(1.0f, 1.0f, 0.0f, 1.0f).visible(true).build();
            targetWaypointLineT = new Line.Builder(targetRegion.topLeft(), targetRegion.topRight())
                    .color(1.0f, 1.0f, 0.0f, 1.0f).visible(true).build();
            targetWaypointLineB = new Line.Builder(targetRegion.bottomLeft(), targetRegion.bottomRight())
                    .color(1.0f, 1.0f, 0.0f, 1.0f).visible(true).build();
        }
        else {
            waypointLine.setPoints(myCar.getPosition(), targetRegion.center());
            lastPassedWaypointLineL.setPoints(lastPassedRegion.topLeft(), lastPassedRegion.bottomLeft());
            lastPassedWaypointLineR.setPoints(lastPassedRegion.topRight(), lastPassedRegion.bottomRight());
            lastPassedWaypointLineT.setPoints(lastPassedRegion.topLeft(), lastPassedRegion.topRight());
            lastPassedWaypointLineB.setPoints(lastPassedRegion.bottomLeft(), lastPassedRegion.bottomRight());
            targetWaypointLineL.setPoints(targetRegion.topLeft(), targetRegion.bottomLeft());
            targetWaypointLineR.setPoints(targetRegion.topRight(), targetRegion.bottomRight());
            targetWaypointLineT.setPoints(targetRegion.topLeft(), targetRegion.topRight());
            targetWaypointLineB.setPoints(targetRegion.bottomLeft(), targetRegion.bottomRight());
        }
        //waypointLine.commit();
        lastPassedWaypointLineL.commit();
        lastPassedWaypointLineR.commit();
        lastPassedWaypointLineT.commit();
        lastPassedWaypointLineB.commit();
        targetWaypointLineL.commit();
        targetWaypointLineR.commit();
        targetWaypointLineT.commit();
        targetWaypointLineB.commit();

        // Run state machine
        State prevState = state;
        switch (state) {
            case STOP:
                break;
            case CRUISING:
                onCruising();
                break;
            case DEFENSIVE_DRIVING:
                onDefensiveDriving();
                break;
            case AGGRESSIVE_DRIVING:
                break;
            case EMERGENCY_ESCAPING:
                onEmergencyEscaping();
                break;
            case OVERTAKING:
                onOvertaking();
                break;
        }

        if (state == prevState) {
            tickTransitionTime(1);
            if (stayingTimeLeftOnState <= 0) {
                transition(state.returnState());
            }
        }
    }

    /**
     *
     * @param tickCount
     */
    private void tickTransitionTime(int tickCount) {
        stayingTimeLeftOnState -= tickCount;
        stayingTimeOnState += tickCount;
    }

    /**
     *
     * @param tickCount
     */
    private void extendTransitionTime(int tickCount) {
        stayingTimeLeftOnState += tickCount;
    }

    /**
     *
     * @param laneSelection
     */
    private void setPathSelection(Track.LaneSelection laneSelection) {
        if (this.laneSelection != laneSelection) {
            int newTargetWaypointIndex = findSuitableWaypointForNewTarget(laneSelection,
                    lastWaypointPassedIndex, lastWaypointPassedIndex,2);
            if (newTargetWaypointIndex == -1) {
                newTargetWaypointIndex =
                        track.findNextValidWaypoint(laneSelection, lastWaypointPassedIndex);
            }
            this.laneSelection = laneSelection;
            this.targetWaypointIndex = newTargetWaypointIndex;
            setNewWaypointToTarget();
        }
    }

    /**
     *
     * @return
     */
    private Track.LaneSelection getPathSelection() {
        return laneSelection;
    }

    /**
     *
     * @return
     */
    private int getLap() {
        return lap;
    }

    /**
     *
     * @return
     */
    public int getRank() {
        return rank;
    }

    /**
     *
     * @param rank
     */
    void setRank(int rank) {
        this.rank = rank;
        if (myCar != null) {
            float rankModifier = 1.0f + rank*(0.1f/8);
            myCar.addEffect(new Effect(rankModifier, 1));
        }
    }

    private final int STARTING_WAYPOINT_INDEX = 30;
    private final int MIN_WAYPOINT_SEARCH_PERIOD = 1;
    private final int DEFENSIVE_DRIVING_RELEASE_COUNT = 3;
    private final float EMERGENCY_ESCAPING_STATE_VELOCITY_LIMIT = 2.0f;
    private final float OVERTAKING_ENTER_POSSIBILITY = 0.01f;
    private final float OVERTAKING_EXTEND_POSSIBILITY = 0.1f;
    private final int OVERTAKING_EXTEND_TICKCOUNT = 10;
    private final int OVERTAKING_PENALTY_ON_BRAKING = 30;

    private String name;
    private Car myCar;
    private Track track;
    private RectF targetRegion;

    private ArrayList<Car> cars;
    private ArrayList<CollidableObject> obstacles;

    // Waypoints
    private int lastWaypointPassedIndex = 0;
    private int targetWaypointIndex = STARTING_WAYPOINT_INDEX;
    private int nextWaypointSearchTimeLeft = MIN_WAYPOINT_SEARCH_PERIOD;
    private Track.LaneSelection laneSelection = Track.LaneSelection.MIDDLE_LANE;

    // state-machine
    private State state;
    private int stayingTimeLeftOnState = Integer.MAX_VALUE;
    private int stayingTimeOnState = 0;
    private int defensiveDrivingReleaseCount = 0;
    private int lap = 0;
    private int rank = 0;
    private boolean finishLineCheckerDone = false;

    // debugging
    private Line waypointLine;
    private Line leftLaneCheckingLine;
    private Line rightLaneCheckingLine;
    private Line lastPassedWaypointLineL, lastPassedWaypointLineR, lastPassedWaypointLineT,
            lastPassedWaypointLineB;
    private Line targetWaypointLineL, targetWaypointLineR, targetWaypointLineT,
            targetWaypointLineB;
}
