// Strings
export const BASE_URL_WS = "ws://localhost:8080/overlay";

// Non Styling configs
export const WEBSOCKET_RECONNECT_TIME = 5000; // ms

// Timings
export const WIDGET_TRANSITION_TIME = 300; // ms
export const QUEUE_ROW_TRANSITION_TIME = 1000; // ms
export const QUEUE_ROW_APPEAR_TIME = QUEUE_ROW_TRANSITION_TIME; // ms
export const QUEUE_ROW_FTS_TRANSITION_TIME = 3000; // ms


// Styles
export const VERDICT_OK = "#1b8041";
export const VERDICT_NOK = "#881f1b";
export const VERDICT_UNKNOWN = "#a59e0c";


export const QUEUE_ROW_HEIGHT = 41; // px
export const QUEUE_FTS_PADDING = QUEUE_ROW_HEIGHT / 2; // px
export const QUEUE_ROWS_COUNT = 15; // n
export const QUEUE_OPACITY = 0.8;

export const CELL_FONT_FAMILY = "Helvetica, serif";
export const CELL_FONT_SIZE = "22pt";
export const CELL_TEXT_COLOR = "white";
export const CELL_BG_COLOR = "rgba(1, 1, 1, 1)";
export const CELL_BG_COLOR_ODD = "rgba(1, 1, 1, 0.9)";

export const CELL_PROBLEM_LINE_WIDTH = "5px"; // css property
export const CELL_QUEUE_VERDICT_WIDTH = "50px"; // css property
export const CELL_QUEUE_RANK_WIDTH = "50px"; // css property
export const CELL_QUEUE_TOTAL_SCORE_WIDTH = "50px"; // css property
export const CELL_QUEUE_TASK_WIDTH = "50px"; // css property

export const CELL_NAME_LEFT_PADDING = "5px"; // css property
export const CELL_NAME_RIGHT_PADDING = CELL_NAME_LEFT_PADDING; // css property
export const CELL_NAME_FONT = CELL_FONT_SIZE + " " + CELL_FONT_FAMILY;
export const CELL_QUEUE_NAME_WIDTH = 315; //px
export const CELL_SCOREBOARD_NAME_WIDTH = 300; //px



export const STAR_SIZE = 10; // px


// Medals
export const getMedalColor = (rank) => {
    switch (true) {
    case (rank < 5):
        return "gold";
    case (rank < 10):
        return "silver";
    case (rank < 15):
        return "#7f4c19";
    default:
        return undefined;
    }
};

// Debug Behaviour
export const LOG_LINES = 300;