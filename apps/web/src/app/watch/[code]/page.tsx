import { LivestreamViewer } from "@/features/livestream/components/LivestreamViewer";

export default async function WatchPage(props: PageProps<"/watch/[code]">) {
  const { code } = await props.params;
  return <LivestreamViewer code={code} />;
}
