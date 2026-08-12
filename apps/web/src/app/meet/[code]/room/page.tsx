import { MeetingRoomView } from "@/features/meeting/components/MeetingRoomView";

export default async function MeetingRoomPage(props: PageProps<"/meet/[code]/room">) {
  const { code } = await props.params;
  return <MeetingRoomView code={code} />;
}
