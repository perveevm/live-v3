import React, { useEffect, useLayoutEffect, useRef, useState } from "react";
import styled, { keyframes } from "styled-components";
import { useDispatch, useSelector } from "react-redux";
import { SCOREBOARD_TYPES } from "../../../consts";
import { ProblemCell, RankCell, TextShrinkingCell } from "../../atoms/ContestCells";
import { Cell } from "../../atoms/Cell";
import { StarIcon } from "../../atoms/Star";
import { TEAM_VIEW_OPACITY, TEAM_VIEW_APPEAR_TIME, VERDICT_NOK, VERDICT_OK, VERDICT_UNKNOWN } from "../../../config";
import { pushLog } from "../../../redux/debug";
import { DateTime } from "luxon";
import _ from "lodash";

const slideIn = keyframes`
  from {
    opacity: 0.1;
  }
  to {
     opacity: 1;
  }
`;

const slideOut = keyframes`
  from {
     opacity: 1;
  }
  to {
      opacity: 0.1;
  }
`;

const NUMWIDTH = 80;
const NAMEWIDTH = 300;
const STATWIDTH = 80;

const ScoreboardCell = styled(Cell)`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 0;
  position: relative;
`;

const ScoreboardStatCell = styled(ScoreboardCell)`
  width: ${STATWIDTH}px;
`;

const TeamViewContainer = styled.div`
  width: 100%;
  height: 100%;
  display: ${props => props.show ? "flex" : "none"};
  flex-direction: column;
  justify-content: start;
  align-items: flex-end;
  position: relative;
  animation: ${props => props.animation} ${TEAM_VIEW_APPEAR_TIME}ms ${props => props.animationStyle};
  animation-fill-mode: forwards;
`;

const TeamInfoWrapper = styled.div`
  display: flex;
  position: relative;
  height: 100%;
`;

const ScoreboardTaskCellWrap = styled(ScoreboardCell)`
  flex-grow: 1;
  flex-shrink: 1;
  flex-basis: 100%;
  height: 100%;
  padding: 5px;
  min-width: 40px;
`;

const TeamTaskStatus = Object.freeze({
    solved: 1,
    failed: 2,
    untouched: 3,
    unknown: 4,
    first: 5
});

const TeamTaskColor = Object.freeze({
    [TeamTaskStatus.solved]: VERDICT_OK,
    [TeamTaskStatus.failed]: VERDICT_NOK,
    [TeamTaskStatus.untouched]: undefined,
    [TeamTaskStatus.unknown]: VERDICT_UNKNOWN,
    [TeamTaskStatus.first]: VERDICT_OK,
});

const TeamTaskSymbol = Object.freeze({
    [TeamTaskStatus.solved]: "+",
    [TeamTaskStatus.failed]: "-",
    [TeamTaskStatus.untouched]: "",
    [TeamTaskStatus.unknown]: "?",
    [TeamTaskStatus.first]: "+",
});

const ScoreboardTaskCell = ({ status, attempts }) => {
    return <ScoreboardTaskCellWrap background={TeamTaskColor[status]}>
        {status === TeamTaskStatus.first && <StarIcon/>}
        {TeamTaskSymbol[status]}
        {status !== TeamTaskStatus.untouched && attempts > 0 && attempts}
    </ScoreboardTaskCellWrap>;
};

const StatisticsProblemCell = styled(ProblemCell)`
  padding: 0 10px;
  width: 50px;
  box-sizing: border-box;
`;

const ScoreboardTimeCell = styled(ScoreboardCell)`
   flex-grow: 1;
  flex-shrink: 1;
  flex-basis: 100%;
  height: 100%;
  padding: 5px;
  padding-left: 10px;
  min-width: 40px;
`;

function getStatus(isFirstToSolve, isSolved, pendingAttempts, wrongAttempts) {
    if (isFirstToSolve) {
        return TeamTaskStatus.first;
    } else if (isSolved) {
        return TeamTaskStatus.solved;
    } else if (pendingAttempts > 0) {
        return TeamTaskStatus.unknown;
    } else if (wrongAttempts > 0) {
        return TeamTaskStatus.failed;
    } else {
        return TeamTaskStatus.untouched;
    }
}

const ScoreboardColumnWrapper = styled.div`
    display: grid;
    opacity: ${TEAM_VIEW_OPACITY};
    grid-template-columns: repeat(2, auto);
    grid-auto-rows: 1fr;
    position: relative;
`;
const ScoreboardTeamInfoRow = styled.div`
    grid-column-start: 1;
    grid-column-end: 3;
`;
const TaskRow = styled.div`
    display: flex;
    width: 100%;
    grid-column-start: 2;
    grid-column-end: 3;
`;

const ScoreboardColumn = ({ teamId }) => {
    let scoreboardData = useSelector((state) => state.scoreboard[SCOREBOARD_TYPES.normal]?.ids[teamId]);
    for (let i = 0; i < scoreboardData?.problemResults.length; i++) {
        scoreboardData.problemResults[i]["index"] = i;
    }
    const tasks = useSelector(state => state.contestInfo?.info?.problems);
    return <ScoreboardColumnWrapper>
        <ScoreboardTeamInfoRow>
            <TeamInfo teamId={teamId}/>
        </ScoreboardTeamInfoRow>
        {_.sortBy(scoreboardData?.problemResults, "lastSubmitTimeMs").flatMap(({
            wrongAttempts,
            pendingAttempts,
            isSolved,
            isFirstToSolve,
            lastSubmitTimeMs,
            index
        }, i) =>
            getStatus(isFirstToSolve, isSolved, pendingAttempts, wrongAttempts) === TeamTaskStatus.untouched ? null :
                <TaskRow key={i}>
                    <ScoreboardTimeCell>{DateTime.fromMillis(lastSubmitTimeMs).toFormat("H:mm")}</ScoreboardTimeCell>
                    <StatisticsProblemCell probData={tasks[index]}/>
                    <ScoreboardTaskCell status={getStatus(isFirstToSolve, isSolved, pendingAttempts, wrongAttempts)}
                        attempts={wrongAttempts + pendingAttempts}/>
                </TaskRow>
        )}
    </ScoreboardColumnWrapper>;
};

const TeamInfo = ({ teamId }) => {
    const teamData = useSelector((state) => state.contestInfo.info?.teamsId[teamId]);
    const scoreboardData = useSelector((state) => state.scoreboard[SCOREBOARD_TYPES.normal]?.ids[teamId]);
    return <TeamInfoWrapper>
        <RankCell rank={scoreboardData?.rank} width={NUMWIDTH + "px"} medal={scoreboardData?.medalType}/>
        <TextShrinkingCell text={teamData?.shortName} width={NAMEWIDTH + "px"} canGrow={false} canShrink={false}/>
        <ScoreboardStatCell>
            {scoreboardData?.totalScore}
        </ScoreboardStatCell>
        <ScoreboardStatCell>
            {scoreboardData?.penalty}
        </ScoreboardStatCell>

    </TeamInfoWrapper>;
};

const TeamImageWrapper = styled.img`
    position: absolute;
    width: 100%;
    top: 0;
`;

const TeamVideoWrapper = styled.video`
    position: absolute;
    width: 100%;
    top: 0;
`;


const TeamWebRTCVideoWrapper = ({ url, setIsLoaded }) => {
    const dispatch = useDispatch();
    const videoRef = useRef();
    const rtcRef = useRef();
    useEffect(() => {
        dispatch(pushLog(`Webrtc content from ${url}`));
        rtcRef.current = new RTCPeerConnection();
        rtcRef.current.ontrack = function (event) {
            if (event.track.kind !== "video") {
                return;
            }
            videoRef.current.srcObject = event.streams[0];
        };
        rtcRef.current.addTransceiver("video");
        rtcRef.current.addTransceiver("audio");
        rtcRef.current.createOffer()
            .then(offer => {
                rtcRef.current.setLocalDescription(offer);
                return fetch(url, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(offer),
                });
            })
            .then(res => res.json())
            .then(res => rtcRef.current.setRemoteDescription(res))
            .catch(e => dispatch(pushLog("ERROR featching  webrtc peer connection info: " + e)));

        return () => rtcRef.current?.close();
    }, [url]);
    return (<TeamVideoWrapper
        ref={videoRef}
        onCanPlay={() => setIsLoaded(true)}
        onError={() => setIsLoaded(false) || dispatch(pushLog("ERROR on loading image in Picture widget"))}
        autoPlay
        muted/>);
};

const TeamVideoAnimationWrapper = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: start;
  align-items: center;
`;


const TeamViewPInPWrapper = styled.div`
  position: absolute;   
  width: ${({ sizeX }) => `${sizeX * 0.4}px`};
  height: ${({ sizeX }) => `${sizeX * 0.5625 * 0.4}px`};
  right: 0;
  top: ${({ sizeX }) => `${sizeX * 0.5625 * 0.6}px`};
`;

const teamViewComponentRender = {
    TaskStatus: ({ onLoadStatus, teamId }) => {
        useLayoutEffect(() => onLoadStatus(true),
            []);
        return <ScoreboardColumn teamId={teamId}/>;
    },
    Photo: ({ onLoadStatus, url }) => {
        return <TeamVideoAnimationWrapper>
            <TeamImageWrapper src={url} onLoad={() => onLoadStatus(true)}/>
        </TeamVideoAnimationWrapper>;
    },
    Video: ({ onLoadStatus, url }) => {
        return <TeamVideoAnimationWrapper>
            <TeamVideoWrapper
                src={url}
                onCanPlay={() => onLoadStatus(true)}
                onError={() => onLoadStatus(false)}
                autoPlay
                muted/>
        </TeamVideoAnimationWrapper>;
    },
    WebRTCConnection: ({ onLoadStatus, url }) => {
        return <TeamVideoAnimationWrapper>
            <TeamWebRTCVideoWrapper url={url} setIsLoaded={onLoadStatus}/>
        </TeamVideoAnimationWrapper>;
    },
};

export const TeamView = ({ widgetData: { settings, location }, transitionState }) => {
    const [loadedComponents, setLoadedComponents] = useState(0);
    const isLoaded = loadedComponents === (1 << settings.content.length) - 1;
    const mediaContent = settings.content.filter(e => e.isMedia).map((e, index) => ({ ...e, pInP: index > 0 }));
    return <TeamViewContainer
        show={isLoaded}
        animation={isLoaded && (transitionState === "exiting" ? slideOut : slideIn)}
        animationStyle={transitionState === "exiting" ? "ease-in" : "ease-out"}
    >
        {mediaContent.concat(settings.content.filter(e => !e.isMedia)).map((c, index) => {
            const onLoadStatus = (v) => setLoadedComponents(m => v ? (m | (1 << index)) : (m & ~(1 << index)));
            const Component = teamViewComponentRender[c.type];
            if (Component === undefined) {
                useEffect(() => onLoadStatus(true),
                    [c.teamId]);
                return undefined;
            }
            const element = <Component onLoadStatus={onLoadStatus} key={index} {...c}/>;
            if (c.pInP) {
                return <TeamViewPInPWrapper sizeX={location.sizeX}>{element}</TeamViewPInPWrapper>;
            }
            return element;
        })}
    </TeamViewContainer>;
};
TeamView.ignoreAnimation = true;
TeamView.overrideTimeout = TEAM_VIEW_APPEAR_TIME;

export default TeamView;
