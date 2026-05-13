// WetzelBulk - Todd's Workout Tracker
let currentCycleIndex = 0;
const cycle = ['Push', 'Legs', 'Pull', 'Cardio'];

function getNextWorkout() {
    // Simple version for now - we'll make it remember last workout soon
    return cycle[currentCycleIndex];
}

function startWorkout() {
    const workout = getNextWorkout();
    alert(`Starting ${workout} Day\n\n(Full logging screen coming next!)`);
}

function changeDay() {
    let options = cycle.join(", ");
    const newDay = prompt(`Enter new day:\n${options}`, getNextWorkout());
    if (newDay && cycle.includes(newDay)) {
        currentCycleIndex = cycle.indexOf(newDay);
        document.getElementById('today-workout').textContent = newDay + " Day";
        alert(`Switched to ${newDay} Day`);
    }
}

// Initialize
document.getElementById('today-workout').textContent = getNextWorkout() + " Day";
