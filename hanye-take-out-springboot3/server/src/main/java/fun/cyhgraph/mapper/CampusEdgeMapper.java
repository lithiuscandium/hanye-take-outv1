package fun.cyhgraph.mapper;

import fun.cyhgraph.entity.CampusEdge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CampusEdgeMapper {

    @Select("select * from campus_edge where status = 1")
    List<CampusEdge> listActive();
}

