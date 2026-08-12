import { LobbyView } from "@/features/lobby/components/LobbyView";

export default async function LobbyPage(props: PageProps<"/meet/[code]">) {
  const { code } = await props.params;
  return <LobbyView code={code} />;
}
